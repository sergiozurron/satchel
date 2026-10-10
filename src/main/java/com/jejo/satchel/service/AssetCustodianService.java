package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fireblocks.sdk.ApiException;
import com.fireblocks.sdk.ApiResponse;
import com.fireblocks.sdk.Fireblocks;
import com.fireblocks.sdk.model.AssetWallet;
import com.fireblocks.sdk.model.CreateAssetsRequest;
import com.fireblocks.sdk.model.CreateTransactionResponse;
import com.fireblocks.sdk.model.CreateVaultAccountRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest.VaultTypeEnum;
import com.fireblocks.sdk.model.CreateVaultAssetResponse;
import com.jejo.satchel.exception.AssetCustodianApiException;

import lombok.extern.slf4j.Slf4j;

import com.fireblocks.sdk.model.DestinationTransferPeerPath;
import com.fireblocks.sdk.model.OneTimeAddress;
import com.fireblocks.sdk.model.PaginatedAssetWalletResponse;
import com.fireblocks.sdk.model.SourceTransferPeerPath;
import com.fireblocks.sdk.model.TransactionRequest;
import com.fireblocks.sdk.model.TransactionRequest.FeeLevelEnum;
import com.fireblocks.sdk.model.TransactionRequestAmount;
import com.fireblocks.sdk.model.TransferPeerPathType;
import com.fireblocks.sdk.model.VaultAccount;
import com.fireblocks.sdk.model.VaultAccountsPagedResponse;
import com.fireblocks.sdk.model.VaultAsset;

@Slf4j
@Service
public class AssetCustodianService {

	@Value("#{${satchel.financial.assets.deposit-sweep-minimum}}")
	private Map<String, BigDecimal> assetDepositSweepMinimums;
	@Value("#{${satchel.financial.assets.base-asset}}")
	private Map<String, String> assetBaseAsset;

	@Value("${satchel.custodian.account.withdrawal.id}")
	public String withdrawalId;
	@Value("${satchel.custodian.account.withdrawal.address}")
	public String withdrawalAddress;

	@Value("${satchel.custodian.account.omnibus.id}")
	public String omnibusId;
	@Value("${satchel.custodian.account.omnibus.address}")
	public String omnibusAddress;

	@Value("${satchel.custodian.webhook.transaction.created}")
	public String webhookTransactionCreated;
	@Value("${satchel.custodian.webhook.transaction.updated}")
	public String webhookTransactionStatusUpdated;
	@Value("${satchel.custodian.webhook.balance.updated}")
	public String webhookBalanceUpdate;

	@Value("${satchel.custodian.transaction.status.completed}")
	public String transactionStatusCompleted;
	@Value("${satchel.custodian.transaction.substatus.confirmed}")
	public String transactionSubstatusConfirmed;

	private final Fireblocks fireblocks;

	public AssetCustodianService(Fireblocks fireblocks) {
		this.fireblocks = fireblocks;
	}

	public String createVaultAccount(String vaultName) {
		String vaultId = null;
		CreateVaultAccountRequest request = new CreateVaultAccountRequest().name(vaultName)
				.vaultType(VaultTypeEnum.MPC).hiddenOnUI(true);
		String idempotencyKey = Integer.toString(new Random().nextInt()); // Valid for 24 hours
		try {
			CompletableFuture<ApiResponse<VaultAccount>> response = fireblocks.vaults()
					.createVaultAccount(request, idempotencyKey);
			vaultId = response.get().getData().getId();
			log.info("Created vault account with name: {}, id: {}", vaultName, vaultId);
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return vaultId;
	}

	public String createVaultWallet(String vaultAccountId, String assetId) {
		String walletAddress = null;
		CreateAssetsRequest createAssetsRequest = new CreateAssetsRequest();
		String idempotencyKey = Integer.toString(new Random().nextInt());
		try {
			CompletableFuture<ApiResponse<CreateVaultAssetResponse>> response = fireblocks.vaults()
					.createVaultAccountAsset(vaultAccountId.toString(), assetId,
							createAssetsRequest, idempotencyKey);
			walletAddress = response.get().getData().getAddress();
			log.info("Created vault wallet for vaultAccountId: {}, assetId: {}, address: {}",
					vaultAccountId, assetId, walletAddress);
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return walletAddress;
	}

	public List<VaultAccount> findAllVaultAccountsBySuffix(String nameSuffix) {
		return findAllVaultAccounts(null, nameSuffix, null, null, null, null, null, null, null);
	}

	public List<VaultAccount> findAllVaultAccountsByPrefixAndMinAmountAndAsset(String namePrefix,
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
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return accounts;
	}

	public List<AssetWallet> findAllAssetWallets() {
		List<AssetWallet> assets = new ArrayList<>();
		try {
			CompletableFuture<ApiResponse<PaginatedAssetWalletResponse>> response = fireblocks
					.vaults().getAssetWallets(null, null, null, null, null, null);
			log.info("Retrieved {} vault assets", assets.size());
			assets = response.get().getData().getAssetWallets();
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return assets;
	}

	public BigDecimal getVaultAccountAssetBalance(String vaultAccountId, String assetId) {
		BigDecimal balance = BigDecimal.ZERO;
		try {
			CompletableFuture<ApiResponse<VaultAsset>> response = fireblocks.vaults()
					.getVaultAccountAsset(vaultAccountId, assetId);
			balance = new BigDecimal(response.get().getData().getTotal());
			log.info("Retrieved balance for vaultAccountId: {}, assetId: {}. Balance: {}",
					vaultAccountId, assetId, balance);
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return balance;
	}

	public void sweepDepositsToOmnibus() {
		List<VaultAccount> depositAccounts = findAllVaultAccountsByPrefixAndMinAmountAndAsset(
				"deposit-", BigDecimal.ZERO, null);
		depositAccounts.forEach(account -> {
			account.getAssets().forEach(asset -> {
				if (assetBaseAsset.get(asset.getId()).equals(asset.getId()) && new BigDecimal(asset.getAvailable()).compareTo(BigDecimal.ZERO) == 0) {
					createTransaction(asset.getId(),
							new SourceTransferPeerPath().id(account.getId())
									.type(TransferPeerPathType.VAULT_ACCOUNT),
							new DestinationTransferPeerPath().id(omnibusId)
									.type(TransferPeerPathType.VAULT_ACCOUNT),
							new BigDecimal(asset.getAvailable()));
					
				}
				if (new BigDecimal(asset.getAvailable())
						.compareTo(assetDepositSweepMinimums.get(asset.getId())) > 0) {
					createTransaction(asset.getId(),
							new SourceTransferPeerPath().id(account.getId())
									.type(TransferPeerPathType.VAULT_ACCOUNT),
							new DestinationTransferPeerPath().id(omnibusId)
									.type(TransferPeerPathType.VAULT_ACCOUNT),
							new BigDecimal(asset.getAvailable()));
				}
			});
		});
	}

	public String createTransactionFromOmnibus(String assetId, String destinationAddress,
			BigDecimal amount) {
		return createTransaction(assetId,
				new SourceTransferPeerPath().id(omnibusId).type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath()
						.oneTimeAddress(new OneTimeAddress().address(destinationAddress))
						.type(TransferPeerPathType.ONE_TIME_ADDRESS),
				amount);
	}

	public String createTransactionFromWithdrawal(String assetId, String destinationAddress,
			BigDecimal amount) {
		return createTransaction(assetId,
				new SourceTransferPeerPath().id(withdrawalId)
						.type(TransferPeerPathType.VAULT_ACCOUNT),
				new DestinationTransferPeerPath()
						.oneTimeAddress(new OneTimeAddress().address(destinationAddress))
						.type(TransferPeerPathType.ONE_TIME_ADDRESS),
				amount);
	}

	public String createTransaction(String assetId, SourceTransferPeerPath source,
			DestinationTransferPeerPath destination, BigDecimal amount) {
		String transactionId = null;
		TransactionRequest transactionRequest = new TransactionRequest().assetId(assetId)
				.treatAsGrossAmount(true).source(source).destination(destination)
				.feeLevel(FeeLevelEnum.LOW).amount(new TransactionRequestAmount(amount));
		try {
			CompletableFuture<ApiResponse<CreateTransactionResponse>> response = fireblocks
					.transactions().createTransaction(transactionRequest, null, null);
			transactionId = response.get().getData().getId();
			log.info(
					"Created transaction with id: {}, assetId: {}, amount: {}, source: {}, destination: {}",
					transactionId, assetId, amount, source.getId(), destination.getId());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return transactionId;
	}

	public BigDecimal getOmnibusBalance(String assetId) {
		return getVaultAccountAssetBalance(omnibusId, assetId);
	}

}
