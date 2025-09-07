package com.jejo.satchel.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fireblocks.sdk.ApiException;
import com.fireblocks.sdk.ApiResponse;
import com.fireblocks.sdk.Fireblocks;
import com.fireblocks.sdk.model.CreateAssetsRequest;
import com.fireblocks.sdk.model.CreateTransactionResponse;
import com.fireblocks.sdk.model.CreateVaultAccountRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest.VaultTypeEnum;
import com.jejo.satchel.exception.AssetCustodianApiException;
import com.fireblocks.sdk.model.CreateVaultAssetResponse;
import com.fireblocks.sdk.model.DestinationTransferPeerPath;
import com.fireblocks.sdk.model.SourceTransferPeerPath;
import com.fireblocks.sdk.model.TransactionRequest;
import com.fireblocks.sdk.model.TransactionRequest.FeeLevelEnum;
import com.fireblocks.sdk.model.TransactionRequestAmount;
import com.fireblocks.sdk.model.VaultAccount;
import com.fireblocks.sdk.model.VaultAccountsPagedResponse;
import com.fireblocks.sdk.model.VaultAsset;

@Service
public class AssetCustodianService {

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
			ApiException apiException = (ApiException)e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return vaultId;
	}

	public String createWallet(String vaultAccountId, String assetId) {
		String walletAddress = null;
		CreateAssetsRequest createAssetsRequest = new CreateAssetsRequest();
		String idempotencyKey = Integer.toString(new Random().nextInt());
		try {
			CompletableFuture<ApiResponse<CreateVaultAssetResponse>> response = fireblocks.vaults()
					.createVaultAccountAsset(vaultAccountId, assetId, createAssetsRequest,
							idempotencyKey);
			walletAddress = response.get().getData().getAddress();
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException)e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return walletAddress;
	}

	public List<VaultAccount> findAllVaultAccounts(String nameSuffix) {
		List<VaultAccount> accounts = new ArrayList<>();
		try {
			CompletableFuture<ApiResponse<VaultAccountsPagedResponse>> response = fireblocks
					.vaults().getPagedVaultAccounts(null, nameSuffix, null, null, null, null, null,
							null, null);
			accounts = response.get().getData().getAccounts().stream()
					.filter(account -> account.getName().endsWith(nameSuffix))
					.collect(Collectors.toList());
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException)e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return accounts;
	}

	public BigDecimal getVaultAccountAssetBalance(Long vaultAccountId, String assetId) {
		BigDecimal balance = BigDecimal.ZERO;
		try {
			CompletableFuture<ApiResponse<VaultAsset>> response = fireblocks.vaults()
					.getVaultAccountAsset(vaultAccountId.toString(), assetId);
			balance = new BigDecimal(response.get().getData().getTotal());
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException)e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return balance;
	}

	public String createTransaction(String assetId, SourceTransferPeerPath source,
			DestinationTransferPeerPath destinationVaultAccountId, BigDecimal amount) {
		String transactionId = null;
		TransactionRequest transactionRequest = new TransactionRequest().assetId(assetId)
				.source(source).destination(destinationVaultAccountId).feeLevel(FeeLevelEnum.HIGH)
				.amount(new TransactionRequestAmount(amount));
		try {
			CompletableFuture<ApiResponse<CreateTransactionResponse>> response = fireblocks
					.transactions().createTransaction(transactionRequest, null, null);
			transactionId = response.get().getData().getId();
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException)e.getCause();
			throw new AssetCustodianApiException(apiException.getResponseBody());
		} catch (ApiException e) {
			throw new AssetCustodianApiException(e.getResponseBody());
		}
		return transactionId;
	}

}
