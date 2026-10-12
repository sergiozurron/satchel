package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

import com.fireblocks.sdk.ApiException;
import com.fireblocks.sdk.ApiResponse;
import com.fireblocks.sdk.BasePath;
import com.fireblocks.sdk.ConfigurationOptions;
import com.fireblocks.sdk.Fireblocks;
import com.fireblocks.sdk.model.CreateAssetsRequest;
import com.fireblocks.sdk.model.CreateTransactionResponse;
import com.fireblocks.sdk.model.CreateVaultAccountRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest.VaultTypeEnum;
import com.fireblocks.sdk.model.CreateVaultAssetResponse;

import lombok.extern.slf4j.Slf4j;

import com.fireblocks.sdk.model.DestinationTransferPeerPath;
import com.fireblocks.sdk.model.OneTimeAddress;
import com.fireblocks.sdk.model.PaginatedAddressResponse;
import com.fireblocks.sdk.model.SourceTransferPeerPath;
import com.fireblocks.sdk.model.TransactionRequest;
import com.fireblocks.sdk.model.TransactionRequest.FeeLevelEnum;
import com.fireblocks.sdk.model.TransactionRequestAmount;
import com.fireblocks.sdk.model.TransferPeerPathType;
import com.fireblocks.sdk.model.VaultAccount;
import com.fireblocks.sdk.model.VaultAccountsPagedResponse;
import com.fireblocks.sdk.model.VaultAsset;
import com.fireblocks.sdk.model.VaultWalletAddress;

@Slf4j
public class FireblocksService implements AssetCustodianService {
	
	@Value("#{${satchel.financial.assets.deposit-sweep-minimum}}")
	private Map<String, BigDecimal> assetDepositSweepMinimums;
	@Value("#{${satchel.financial.assets.base-asset}}")
	private Map<String, String> assetBaseAsset;

	@Value("${satchel.custodian.account.omnibus.id}")
	private String omnibusId;

	@Value("${satchel.custodian.account.gas-station.id}")
	private String gasStationId;
	@Value("${satchel.financial.gas-threshold}")
	private BigDecimal gasThreshold;

	@Value("${satchel.name.deposit.prefix}")
	private String depositPrefix;

	private final Fireblocks fireblocks;

	public FireblocksService(String apiKey, String secretKey) {
		ConfigurationOptions configurationOptions = new ConfigurationOptions().basePath(BasePath.Sandbox)
				.apiKey(apiKey).secretKey(secretKey);
		this.fireblocks = new Fireblocks(configurationOptions);
	}

	private List<VaultAccount> findAllVaultAccountsByPrefixAndMinAmountAndAsset(String namePrefix,
			BigDecimal minAmountThreshold, String assetId) {
		return findAllVaultAccounts(namePrefix, null, minAmountThreshold, assetId, null, null, null,
				null, null);
	}

	public List<VaultAccount> findAllVaultAccounts(String namePrefix, String nameSuffix,
			BigDecimal minAmountThreshold, String assetId, String orderBy, String before,
			String after, BigDecimal limit, List<UUID> tagIds) {
		List<VaultAccount> accounts = new ArrayList<>();
		try {
			CompletableFuture<ApiResponse<VaultAccountsPagedResponse>> response = fireblocks
					.vaults().getPagedVaultAccounts(namePrefix, nameSuffix, minAmountThreshold,
							assetId, orderBy, before, after, limit, tagIds);
			accounts = response.get().getData().getAccounts();
			log.info(
					"Retrieved {} vault accounts with prefix: {}, suffix: {}, minAmountThreshold: {}, assetId: {}",
					accounts.size(), namePrefix, nameSuffix, minAmountThreshold, assetId);
		} catch (Exception e) {
			log.error("{}", e.getCause());
		}
		return accounts;
	}

	@Scheduled(initialDelayString = "${satchel.custodian.deposit.sweep.delay}", fixedDelayString = "${satchel.custodian.deposit.sweep.delay}")
	public void sweepDepositsToOmnibus() {
		log.info("Sweeping deposits to omnibus account");
		List<VaultAccount> depositAccounts = findAllVaultAccountsByPrefixAndMinAmountAndAsset(
				"deposit-", BigDecimal.ZERO, null);
		depositAccounts.forEach(account -> {
			// Gather base asset amounts for auto fueling
			Map<String, BigDecimal> baseAssetAmount = new HashMap<>();
			account.getAssets().forEach(asset -> {
				String assetId = asset.getId();
				if (assetBaseAsset.get(assetId).equals(assetId)) {
					baseAssetAmount.put(assetId, new BigDecimal(asset.getAvailable()));
				}
			});
			// Transfer to omnibus or fuel
			account.getAssets().forEach(asset -> {
				String assetId = asset.getId();
				String baseAssetId = assetBaseAsset.get(assetId);
				if (!baseAssetId.equals(assetId) && baseAssetAmount
						.getOrDefault(baseAssetId, BigDecimal.ZERO).compareTo(gasThreshold) < 0) {
					createTransaction(baseAssetId, gasStationId, account.getId(),
							gasThreshold.multiply(BigDecimal.valueOf(2L)));
					log.info("Fueling vault account {} with {}", account.getId(), baseAssetId);
				} else if (new BigDecimal(asset.getAvailable())
						.compareTo(assetDepositSweepMinimums.get(assetId)) > 0) {
					createTransaction(assetId, account.getId(), omnibusId,
							new BigDecimal(asset.getAvailable()));
				}
			});
		});
	}

	private void createTransaction(String assetId, String sourceVaultAccountId,
			String destinationVaultAccountId, BigDecimal amount) {
		SourceTransferPeerPath source = new SourceTransferPeerPath().id(omnibusId)
				.type(TransferPeerPathType.VAULT_ACCOUNT);
		DestinationTransferPeerPath destination = new DestinationTransferPeerPath()
				.id(destinationVaultAccountId).type(TransferPeerPathType.VAULT_ACCOUNT);
		TransactionRequest transactionRequest = new TransactionRequest().assetId(assetId)
				.treatAsGrossAmount(true).source(source).destination(destination)
				.feeLevel(FeeLevelEnum.LOW).amount(new TransactionRequestAmount(amount));
		try {
			CompletableFuture<ApiResponse<CreateTransactionResponse>> response = fireblocks
					.transactions().createTransaction(transactionRequest, null, null);
			String transactionId = response.get().getData().getId();
			log.info(
					"Created transaction with id: {}, assetId: {}, amount: {}, source: {}, destination: {}",
					transactionId, assetId, amount, source.getId(), destination.getId());
		} catch (ApiException | InterruptedException | ExecutionException e) {
			log.error("{}", e.getCause());
		}
	}

	@Override
	public String getDepositAddress(Long userId, String assetId) {
		String address = null;
		String vaultName = depositPrefix + userId;
		try {
			// Find vault account
			CompletableFuture<ApiResponse<VaultAccountsPagedResponse>> getPagedVaultAccountsResponse = fireblocks
					.vaults().getPagedVaultAccounts(vaultName, null, null, null, null, null,
							null, null, null);
			List<String> vaultAccountIds = getPagedVaultAccountsResponse.get().getData()
					.getAccounts().stream().map(VaultAccount::getId).toList();
			// Create vault account if it does not exist
			if (vaultAccountIds.isEmpty()) {
				String vaultId = null;
				CreateVaultAccountRequest request = new CreateVaultAccountRequest().name(vaultName)
						.vaultType(VaultTypeEnum.MPC).hiddenOnUI(true);
				String idempotencyKey = Integer.toString(new Random().nextInt()); // Valid for 24
																					// hours
				CompletableFuture<ApiResponse<VaultAccount>> response = fireblocks.vaults()
						.createVaultAccount(request, idempotencyKey);
				vaultAccountIds.set(0, response.get().getData().getId());
				log.info("Created vault account with name: {}, id: {}", vaultName, vaultId);
			}
			String vaultAccountId = vaultAccountIds.get(0);
			// Find asset wallet
			CompletableFuture<ApiResponse<PaginatedAddressResponse>> vaultAccountResponse = fireblocks
					.vaults().getVaultAccountAssetAddressesPaginated(vaultAccountId, assetId, null,
							null, null);
			List<String> addresses = vaultAccountResponse.get().getData().getAddresses().stream()
					.map(VaultWalletAddress::getAddress).toList();
			// Create asset wallet if it does not exist
			if (addresses.isEmpty()) {
				CreateAssetsRequest createAssetsRequest = new CreateAssetsRequest();
				String idempotencyKey = Integer.toString(new Random().nextInt());
				CompletableFuture<ApiResponse<CreateVaultAssetResponse>> response = fireblocks
						.vaults().createVaultAccountAsset(vaultAccountId.toString(), assetId,
								createAssetsRequest, idempotencyKey);
				addresses.set(0, response.get().getData().getAddress());
				log.info("Created vault wallet for vaultAccountId: {}, assetId: {}, address: {}",
						vaultAccountId, assetId, address);
			}
			address = addresses.get(0);

		} catch (ApiException | InterruptedException | ExecutionException e) {
			log.error("{}", e.getCause());
		}
		return address;
	}

	@Override
	public void createTransaction(String assetId, BigDecimal amount, Long userId,
			String destinationAddress) {
		SourceTransferPeerPath source = new SourceTransferPeerPath().id(omnibusId)
				.type(TransferPeerPathType.VAULT_ACCOUNT);
		DestinationTransferPeerPath destination = new DestinationTransferPeerPath()
				.oneTimeAddress(new OneTimeAddress().address(destinationAddress))
				.type(TransferPeerPathType.ONE_TIME_ADDRESS);
		TransactionRequest transactionRequest = new TransactionRequest().assetId(assetId)
				.treatAsGrossAmount(true).source(source).destination(destination)
				.feeLevel(FeeLevelEnum.LOW).amount(new TransactionRequestAmount(amount));
		try {
			CompletableFuture<ApiResponse<CreateTransactionResponse>> response = fireblocks
					.transactions().createTransaction(transactionRequest, null, null);
			String transactionId = response.get().getData().getId();
			log.info(
					"Created transaction with id: {}, assetId: {}, amount: {}, source: {}, destination: {}",
					transactionId, assetId, amount, source.getId(), destination.getId());
		} catch (ApiException | InterruptedException | ExecutionException e) {
			log.error("{}", e.getCause());
		}
	}

	@Override
	public BigDecimal getPoolSize(String assetId) {
		BigDecimal balance = BigDecimal.ZERO;
		try {
			CompletableFuture<ApiResponse<VaultAsset>> response = fireblocks.vaults()
					.getVaultAccountAsset(omnibusId, assetId);
			balance = new BigDecimal(response.get().getData().getAvailable());
			log.info("Retrieved balance for omnibusId: {}, assetId: {}. Balance: {}",
					omnibusId, assetId, balance);
		} catch (Exception e) {
			log.error("{}", e.getCause());
		}
		return balance;
	}

}
