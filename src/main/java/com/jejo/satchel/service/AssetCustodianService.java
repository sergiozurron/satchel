package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fireblocks.sdk.ApiException;
import com.fireblocks.sdk.ApiResponse;
import com.fireblocks.sdk.Fireblocks;
import com.fireblocks.sdk.model.CreateAssetsRequest;
import com.fireblocks.sdk.model.CreateTransactionResponse;
import com.fireblocks.sdk.model.CreateVaultAccountRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest.VaultTypeEnum;
import com.fireblocks.sdk.model.CreateVaultAssetResponse;
import com.jejo.satchel.exception.AssetCustodianApiException;
import com.fireblocks.sdk.model.DestinationTransferPeerPath;
import com.fireblocks.sdk.model.OneTimeAddress;
import com.fireblocks.sdk.model.SourceTransferPeerPath;
import com.fireblocks.sdk.model.TransactionRequest;
import com.fireblocks.sdk.model.TransactionRequest.FeeLevelEnum;
import com.fireblocks.sdk.model.TransactionRequestAmount;
import com.fireblocks.sdk.model.TransferPeerPathType;
import com.fireblocks.sdk.model.VaultAccount;
import com.fireblocks.sdk.model.VaultAccountsPagedResponse;
import com.fireblocks.sdk.model.VaultAsset;

@Service
public class AssetCustodianService {

	@Value("${custodian.account.withdrawal.id}")
	public String withdrawalId;
	@Value("${custodian.account.withdrawal.address}")
	public String withdrawalAddress;

	@Value("${custodian.account.omnibus.id}")
	public String omnibusId;
	@Value("${custodian.account.omnibus.address}")
	public String omnibusAddress;
	@Value("${custodian.account.omnibus.coin}")
	public String omnibusCoin;

	@Value("${custodian.webhook.transaction.created}")
	public String webhookTransactionCreated;
	@Value("${custodian.webhook.transaction.updated}")
	public String webhookTransactionStatusUpdated;
	@Value("${custodian.webhook.balance.updated}")
	public String webhookBalanceUpdate;

	@Value("${custodian.transaction.status.completed}")
	public String transactionStatusCompleted;
	@Value("${custodian.transaction.substatus.confirmed}")
	public String transactionSubstatusConfirmed;

	private final Fireblocks fireblocks;

	public AssetCustodianService(Fireblocks fireblocks) {
		this.fireblocks = fireblocks;
	}

	public Long createVaultAccount(String vaultName) {
		Long vaultId = null;
		CreateVaultAccountRequest request = new CreateVaultAccountRequest().name(vaultName)
				.vaultType(VaultTypeEnum.MPC).hiddenOnUI(true);
		String idempotencyKey = Integer.toString(new Random().nextInt()); // Valid for 24 hours
		try {
			CompletableFuture<ApiResponse<VaultAccount>> response = fireblocks.vaults()
					.createVaultAccount(request, idempotencyKey);
			vaultId = Long.valueOf(response.get().getData().getId());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return vaultId;
	}

	public String createVaultWallet(Long vaultAccountId, String assetId) {
		String walletAddress = null;
		CreateAssetsRequest createAssetsRequest = new CreateAssetsRequest();
		String idempotencyKey = Integer.toString(new Random().nextInt());
		try {
			CompletableFuture<ApiResponse<CreateVaultAssetResponse>> response = fireblocks.vaults()
					.createVaultAccountAsset(vaultAccountId.toString(), assetId, createAssetsRequest,
							idempotencyKey);
			walletAddress = response.get().getData().getAddress();
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
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
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return accounts;
	}

	public BigDecimal getVaultAccountAssetBalance(String vaultAccountId, String assetId) {
		BigDecimal balance = BigDecimal.ZERO;
		try {
			CompletableFuture<ApiResponse<VaultAsset>> response = fireblocks.vaults()
					.getVaultAccountAsset(vaultAccountId, assetId);
			balance = new BigDecimal(response.get().getData().getTotal());
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return balance;
	}

	public void createTransactionsToOmnibus(List<VaultAccount> sourceAccounts) {
		sourceAccounts.forEach(account -> {
			account.getAssets().forEach(asset -> {
				createTransaction(asset.getId(),
						new SourceTransferPeerPath().id(account.getId())
								.type(TransferPeerPathType.VAULT_ACCOUNT),
						new DestinationTransferPeerPath().id(omnibusId)
								.type(TransferPeerPathType.VAULT_ACCOUNT),
						new BigDecimal(asset.getTotal()));
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
				.source(source).destination(destination).feeLevel(FeeLevelEnum.HIGH)
				.amount(new TransactionRequestAmount(amount));
		try {
			CompletableFuture<ApiResponse<CreateTransactionResponse>> response = fireblocks
					.transactions().createTransaction(transactionRequest, null, null);
			transactionId = response.get().getData().getId();
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return transactionId;
	}

	public BigDecimal getOmnibusBalance() {
		return getVaultAccountAssetBalance(omnibusId, omnibusCoin);
	}

}
