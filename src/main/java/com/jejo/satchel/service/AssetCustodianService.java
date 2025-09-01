package com.jejo.satchel.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fireblocks.sdk.ApiException;
import com.fireblocks.sdk.ApiResponse;
import com.fireblocks.sdk.Fireblocks;
import com.fireblocks.sdk.model.CreateAssetsRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest;
import com.fireblocks.sdk.model.CreateVaultAccountRequest.VaultTypeEnum;
import com.fireblocks.sdk.model.CreateVaultAssetResponse;
import com.fireblocks.sdk.model.VaultAccount;
import com.fireblocks.sdk.model.VaultAccountsPagedResponse;

@Service
public class AssetCustodianService {

	@Value("${name.collateral.prefix}")
	public String collateralPrefix;
	@Value("${name.repayment.prefix}")
	public String repaymentPrefix;
	@Value("${name.deposit.prefix}")
	public String depositPrefix;

	private final Fireblocks fireblocks;

	public AssetCustodianService(Fireblocks fireblocks) {
		this.fireblocks = fireblocks;
	}

	public Long createCollateralVaultAccount(String userEmail) {
		return createVaultAccount(collateralPrefix + userEmail, VaultTypeEnum.MPC, true);
	}
	
	public Long createRepaymentVaultAccount(String userEmail) {
		return createVaultAccount(repaymentPrefix + userEmail, VaultTypeEnum.MPC, true);
	}

	public Long createDepositVaultAccount(String userEmail) {
		return createVaultAccount(depositPrefix + userEmail, VaultTypeEnum.MPC, true);
	}
	
	public Long createVaultAccount(String vaultName, VaultTypeEnum vaultType, boolean hiddenOnUI) {
		Long vaultId = null;
		CreateVaultAccountRequest request = new CreateVaultAccountRequest().name(vaultName).vaultType(vaultType)
				.hiddenOnUI(hiddenOnUI);
		String idempotencyKey = Integer.toString(new Random().nextInt()); // Valid for 24 hours
		try {
			CompletableFuture<ApiResponse<VaultAccount>> response = fireblocks.vaults().createVaultAccount(request,
					idempotencyKey);
			vaultId = Long.valueOf(response.get().getData().getId());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			System.err.println("Exception when calling VaultsApi#createVaultAccount");
			System.err.println("Status code: " + apiException.getCode());
			System.err.println("Response headers: " + apiException.getResponseHeaders());
			System.err.println("Reason: " + apiException.getResponseBody());
			e.printStackTrace();
		} catch (ApiException e) {
			System.err.println("Exception when calling VaultsApi#createVaultAccount");
			System.err.println("Status code: " + e.getCode());
			System.err.println("Response headers: " + e.getResponseHeaders());
			System.err.println("Reason: " + e.getResponseBody());
			e.printStackTrace();
		}
		return vaultId;
	}
	
	public String createBTCWallet(String vaultAccountId) {
		return createWallet(vaultAccountId, "BTC_TEST");
	}
	
	public String createUSDCWallet(String vaultAccountId) {
		return createWallet(vaultAccountId, "USDC_ETH_TEST5_AN74");
	}

	public String createWallet(String vaultAccountId, String assetId) {
		String walletAddress = null;
		CreateAssetsRequest createAssetsRequest = new CreateAssetsRequest();
		String idempotencyKey = Integer.toString(new Random().nextInt());
		try {
			CompletableFuture<ApiResponse<CreateVaultAssetResponse>> response = fireblocks.vaults()
					.createVaultAccountAsset(vaultAccountId, assetId, createAssetsRequest, idempotencyKey);
			walletAddress = response.get().getData().getAddress();
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			System.err.println("Exception when calling VaultsApi#createVaultAccountAsset");
			System.err.println("Status code: " + apiException.getCode());
			System.err.println("Response headers: " + apiException.getResponseHeaders());
			System.err.println("Reason: " + apiException.getResponseBody());
			e.printStackTrace();
		} catch (ApiException e) {
			System.err.println("Exception when calling VaultsApi#createVaultAccountAsset");
			System.err.println("Status code: " + e.getCode());
			System.err.println("Response headers: " + e.getResponseHeaders());
			System.err.println("Reason: " + e.getResponseBody());
			e.printStackTrace();
		}
		return walletAddress;
	}

	public List<VaultAccount> findAllVaultAccounts(String nameSuffix) {
		List<VaultAccount> accounts = new ArrayList<>();
		try {
			CompletableFuture<ApiResponse<VaultAccountsPagedResponse>> response = fireblocks.vaults().getPagedVaultAccounts(null, nameSuffix, null, null, null, null, null, null, null);
			accounts = response.get().getData().getAccounts().stream()
					.filter(account -> account.getName().endsWith(nameSuffix))
					.collect(Collectors.toList());
			System.out.println("Status code: " + response.get().getStatusCode());
			System.out.println("Response headers: " + response.get().getHeaders());
			System.out.println("Response body: " + response.get().getData());
		} catch (InterruptedException | ExecutionException e) {
			ApiException apiException = (ApiException) e.getCause();
			System.err.println("Exception when calling VaultsApi#getVaultAccounts");
			System.err.println("Status code: " + apiException.getCode());
			System.err.println("Response headers: " + apiException.getResponseHeaders());
			System.err.println("Reason: " + apiException.getResponseBody());
			e.printStackTrace();
		} catch (ApiException e) {
			System.err.println("Exception when calling VaultsApi#getVaultAccounts");
			System.err.println("Status code: " + e.getCode());
			System.err.println("Response headers: " + e.getResponseHeaders());
			System.err.println("Reason: " + e.getResponseBody());
			e.printStackTrace();
		}
		return accounts;
	}
	
}
