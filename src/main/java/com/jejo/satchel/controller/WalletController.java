package com.jejo.satchel.controller;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jejo.satchel.dto.CreateDepositWalletRequest;
import com.jejo.satchel.dto.Response;
import com.jejo.satchel.dto.WalletResponse;
import com.jejo.satchel.mapper.WalletMapper;
import com.jejo.satchel.service.WalletService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RequestMapping("/api/v1/wallets")
@RestController
public class WalletController {
    
    public final WalletService walletService;
    public final WalletMapper walletMapper;

    public WalletController(WalletService walletService, WalletMapper walletMapper) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> getAllUserWallets(){
        return ResponseEntity.ok(walletMapper.toWalletResponses(walletService.getAllUserWallets()));
    }

    @PostMapping
    public ResponseEntity<Response> createDepositWallet(@Valid @RequestBody CreateDepositWalletRequest request){
        walletService.createDepositWallet(request.getAssetId());
        return ResponseEntity.ok(new Response("Deposit wallet created successfully"));
    }

}
