/*
 * Copyright (c) 2022-2022 Balanced.network.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.balanced.score.core.feehandler;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonValue;
import network.balanced.score.lib.interfaces.FeeHandler;
import score.*;
import score.annotation.External;
import scorex.util.ArrayList;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.utils.Check.*;

public class FeeHandlerImpl implements FeeHandler {
    public static final String TAG = "FeeHandler";

    private final String DIVIDEND_TOKENS = "dividend_tokens";
    private final String LAST_BLOCK = "last_block";
    private final String BLOCK_INTERVAL = "block_interval";
    private final String LAST_TXHASH = "last_txhash";
    private final String ROUTES = "routes";
    private final String GOVERNANCE = "governance";
    private final String ENABLED = "enabled";
    private final String ALLOWED_ADDRESS = "allowed_address";
    private final String NEXT_ALLOWED_ADDRESS_INDEX = "_next_allowed_addresses_index";
    private final String ADMIN_ADDRESS = "admin_address";

    private final ArrayDB<Address> acceptedDividendsTokens = Context.newArrayDB(DIVIDEND_TOKENS, Address.class);
    private final DictDB<Address, BigInteger> lastFeeProcessingBlock = Context.newDictDB(LAST_BLOCK, BigInteger.class);
    private final VarDB<BigInteger> feeProcessingInterval = Context.newVarDB(BLOCK_INTERVAL, BigInteger.class);
    private final VarDB<byte[]> lastTxhash = Context.newVarDB(LAST_TXHASH, byte[].class);
    private final BranchDB<Address, DictDB<Address, String>> routes = Context.newBranchDB(ROUTES, String.class);
    private final VarDB<Address> governance = Context.newVarDB(GOVERNANCE, Address.class);
    private final VarDB<Boolean> enabled = Context.newVarDB(ENABLED, Boolean.class);
    private final ArrayDB<Address> allowedAddress = Context.newArrayDB(ALLOWED_ADDRESS, Address.class);
    private final VarDB<Integer> nextAllowedAddressesIndex = Context.newVarDB(NEXT_ALLOWED_ADDRESS_INDEX,
            Integer.class);
    private final VarDB<Address> admin = Context.newVarDB(ADMIN_ADDRESS, Address.class);

    public FeeHandlerImpl(Address _governance) {
        if (governance.get() == null) {
            isContract(_governance);
            this.governance.set(_governance);
        }
    }

    @External(readonly = true)
    public String name() {
        return "Balanced " + TAG;
    }

    @External
    public void setGovernance(Address _address) {
        onlyOwner();
        isContract(_address);
        governance.set(_address);
    }

    @External(readonly = true)
    public Address getGovernance() {
        return governance.get();
    }

    @External
    public void setAdmin(Address _address) {
        only(governance);
        admin.set(_address);
    }

    @External(readonly = true)
    public Address getAdmin() {
        return admin.get();
    }

    @External
    public void enable() {
        only(admin);
        enabled.set(true);
    }

    @External
    public void disable() {
        only(admin);
        enabled.set(false);
    }

    @External(readonly = true)
    public boolean isEnabled() {
        return enabled.getOrDefault(false);
    }

    @External
    public void setAcceptedDividendTokens(Address[] _tokens) {
        only(admin);
        Context.require(_tokens.length <= 10, TAG + ": There can be a maximum of 10 accepted dividend tokens.");
        for (Address address : _tokens) {
            isContract(address);
        }

        final int currentTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < currentTokensCount; i++) {
            acceptedDividendsTokens.removeLast();
        }

        for (Address token : _tokens) {
            acceptedDividendsTokens.add(token);
        }
    }

    @External(readonly = true)
    public List<Address> getAcceptedDividendTokens() {
        List<Address> tokens = new ArrayList<>();
        int acceptedDividendsTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < acceptedDividendsTokensCount; i++) {
            tokens.add(acceptedDividendsTokens.get(i));
        }
        return tokens;
    }

    @External
    public void setRoute(Address _fromToken, Address _toToken, Address[] _path) {
        only(admin);
        JsonArray path = new JsonArray();
        for (Address address : _path) {
            isContract(address);
            path.add(address.toString());
        }

        routes.at(_fromToken).set(_toToken, path.toString());
    }

    @External
    public void deleteRoute(Address _fromToken, Address _toToken) {
        only(admin);
        routes.at(_fromToken).set(_toToken, null);
    }

    @External(readonly = true)
    public Map<String, Object> getRoute(Address _fromToken, Address _toToken) {
        String path = routes.at(_fromToken).getOrDefault(_toToken, "");
        if (path.equals("")) {
            return Map.of();
        }

        JsonArray pathJson = Json.parse(path).asArray();
        List<String> routePathArray = new ArrayList<>();

        for (JsonValue address : pathJson) {
            routePathArray.add(address.asString());
        }

        return Map.of("fromToken", _fromToken,
                "toToken", _toToken,
                "path", routePathArray);
    }

    @External
    public void setFeeProcessingInterval(BigInteger _interval) {
        only(admin);
        feeProcessingInterval.set(_interval);
    }

    @External(readonly = true)
    public BigInteger getFeeProcessingInterval() {
        return feeProcessingInterval.get();
    }

    @External
    public void tokenFallback(Address _from, BigInteger _value, byte[] _data) {
        Address sender = Context.getCaller();
        if (Arrays.equals(lastTxhash.getOrDefault(new byte[0]), Context.getTransactionHash())) {
            return;
        } else if (!isTimeForFeeProcessing(sender)) {
            return;
        }

        lastTxhash.set(Context.getTransactionHash());
        lastFeeProcessingBlock.set(sender, BigInteger.valueOf(Context.getBlockHeight()));

        int acceptedDividendsTokensCount = acceptedDividendsTokens.size();
        for (int i = 0; i < acceptedDividendsTokensCount; i++) {
            if (acceptedDividendsTokens.get(i).equals(sender)) {
                transferToken(sender, getContractAddress("dividends"), getTokenBalance(sender), new byte[0]);
                return;
            }
        }
    }

    @External
    public void addAllowedAddress(Address address) {
        onlyOwner();
        isContract(address);
        allowedAddress.add(address);
    }

    @External(readonly = true)
    public List<Address> get_allowed_address(int offset) {
        Context.require(offset >= 0, "Negative value not allowed.");

        int end = Math.min(allowedAddress.size(), offset + 20);
        List<Address> addressList = new ArrayList<>();
        for (int i = offset; i < end; i++) {
            addressList.add(allowedAddress.get(i));
        }
        return addressList;
    }

    @External(readonly = true)
    public int getNextAllowedAddressIndex() {
        return nextAllowedAddressesIndex.getOrDefault(0);
    }

    @External
    public void route_contract_balances() {
        int cursor = nextAllowedAddressesIndex.getOrDefault(0);

        Address tokenToRoute = null;
        BigInteger feeCollectedInFeeHandler = null;

        int totalAllowedAddress = allowedAddress.size();
        Context.require(totalAllowedAddress > 0, TAG + ": No allowed addresses.");

        // Search for allowed token with balance > 0
        for (int i = 0; i < totalAllowedAddress; i++) {
            int actualIndex = (i + cursor) % totalAllowedAddress;
            tokenToRoute = allowedAddress.get(actualIndex);
            feeCollectedInFeeHandler = getTokenBalance(tokenToRoute);
            if (feeCollectedInFeeHandler.compareTo(BigInteger.ZERO) > 0) {
                nextAllowedAddressesIndex.set((actualIndex + 1) % totalAllowedAddress);
                break;
            }
            if (i == totalAllowedAddress - 1) {
                throw new UserRevertedException(TAG + ": No fees on the contract.");
            }
        }

        JsonArray path;
        String BALN_CONTRACT = "baln";
        String route = routes.at(tokenToRoute).get(getContractAddress(BALN_CONTRACT));
        if (route == null) {
            path = new JsonArray();
        } else {
            path = Json.parse(route).asArray();
        }

        String ROUTER_CONTRACT = "router";
        String DIVIDENDS_CONTRACT = "dividends";
        String DEX_CONTRACT = "dex";
        if (path.size() > 0) {
            transferToken(tokenToRoute, getContractAddress(ROUTER_CONTRACT), feeCollectedInFeeHandler,
                    createDataFieldRouter(getContractAddress(DIVIDENDS_CONTRACT), path));
        } else {
            transferToken(tokenToRoute, getContractAddress(DEX_CONTRACT), feeCollectedInFeeHandler,
                    createDataFieldDex(getContractAddress(BALN_CONTRACT), getContractAddress(DIVIDENDS_CONTRACT)));
        }
    }

    private byte[] createDataFieldRouter(Address _receiver, JsonArray _path) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", "_swap");
        map.put("params", Map.of("path", _path, "receiver", _receiver.toString()));
        String data = map.toString();
        return data.getBytes();
    }

    private byte[] createDataFieldDex(Address _toToken, Address _receiver) {
        Map<String, Object> map = new HashMap<>();
        map.put("method", "_swap");
        map.put("params", Map.of("toToken", _toToken.toString(), "receiver", _receiver.toString()));
        String data = map.toString();
        return data.getBytes();
    }

    private Address getContractAddress(String _contract) {
        return (Address) Context.call(governance.get(), "getContractAddress", _contract);
    }

    private boolean isTimeForFeeProcessing(Address _token) {
        if (enabled.getOrDefault(Boolean.FALSE).equals(false)) {
            return false;
        }

        BigInteger blockHeight = BigInteger.valueOf(Context.getBlockHeight());
        BigInteger lastConversion = lastFeeProcessingBlock.getOrDefault(_token, BigInteger.ZERO);
        BigInteger targetBlock = lastConversion.add(feeProcessingInterval.getOrDefault(BigInteger.ZERO));

        return blockHeight.compareTo(targetBlock) >= 0;
    }

    private BigInteger getTokenBalance(Address _token) {
        return (BigInteger) Context.call(_token, "balanceOf", Context.getAddress());
    }

    private void transferToken(Address _token, Address _to, BigInteger _amount, byte[] _data) {
        Context.call(_token, "transfer", _to, _amount, _data);
    }
}
