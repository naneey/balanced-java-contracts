/*
 * Copyright (c) 2022-2023 Balanced.network.
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

package network.balanced.score.core.rewards;

import network.balanced.score.core.rewards.utils.BalanceData;
import network.balanced.score.lib.interfaces.DataSourceScoreInterface;
import score.*;
import scorex.util.HashMap;

import java.math.BigInteger;
import java.util.Map;

import static network.balanced.score.core.rewards.utils.RewardsConstants.BALANCE;
import static network.balanced.score.core.rewards.utils.RewardsConstants.TOTAL_SUPPLY;
import static network.balanced.score.lib.utils.Constants.*;

public class DataSourceImpl {
    private final BranchDB<String, VarDB<Address>> contractAddress = Context.newBranchDB("contract_address",
            Address.class);
    private final BranchDB<String, VarDB<String>> name = Context.newBranchDB("name", String.class);
    private final BranchDB<String, VarDB<BigInteger>> day = Context.newBranchDB("day", BigInteger.class);
    private final BranchDB<String, VarDB<Boolean>> precomp = Context.newBranchDB("precomp", Boolean.class);
    private final BranchDB<String, VarDB<Integer>> offset = Context.newBranchDB("offset", Integer.class);
    private final BranchDB<String, VarDB<BigInteger>> workingSupply = Context.newBranchDB("working_supply",
            BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalValue = Context.newBranchDB("total_value",
            BigInteger.class);
    private final BranchDB<String, DictDB<BigInteger, BigInteger>> totalDist = Context.newBranchDB("total_dist",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> distPercent = Context.newBranchDB("dist_percent",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWeight = Context.newBranchDB("user_weight",
            BigInteger.class);
    private final BranchDB<String, DictDB<Address, BigInteger>> userWorkingBalance = Context.newBranchDB(
            "user_working_balance", BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> lastUpdateTimeUs = Context.newBranchDB("last_update_us",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalWeight = Context.newBranchDB("running_total",
            BigInteger.class);
    private final BranchDB<String, VarDB<BigInteger>> totalSupply = Context.newBranchDB("total_supply",
            BigInteger.class);

    private final String dbKey;

    public DataSourceImpl(String key) {
        dbKey = key;
    }

    public Address getContractAddress() {
        return contractAddress.at(dbKey).get();
    }

    public void setContractAddress(Address address) {
        this.contractAddress.at(dbKey).set(address);
    }

    public String getName() {
        return name.at(dbKey).get();
    }

    public void setName(String name) {
        this.name.at(dbKey).set(name);
    }

    public BigInteger getDay() {
        return day.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setDay(BigInteger day) {
        this.day.at(dbKey).set(day);
    }

    // Used for migration if it happens on Claim
    public BigInteger getWorkingSupply(boolean readonly) {
        BigInteger workingSupply = this.workingSupply.at(dbKey).get();
        if (workingSupply != null) {
            return workingSupply;
        }

        workingSupply = loadCurrentSupply(EOA_ZERO).get(TOTAL_SUPPLY);
        if (!readonly) {
            setWorkingSupply(workingSupply);
        }

        return workingSupply;
    }

    // Used for migration if it happens on BalanceUpdate
    public BigInteger getWorkingSupply(BigInteger prevSupply, boolean readonly) {
        BigInteger workingSupply = this.workingSupply.at(dbKey).get();
        if (workingSupply != null) {
            return workingSupply;
        }

        if (!readonly) {
            setWorkingSupply(prevSupply);
        }

        return prevSupply;
    }

    public BigInteger getWorkingSupply() {
        return workingSupply.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    private void setWorkingSupply(BigInteger supply) {
        this.workingSupply.at(dbKey).set(supply);
    }

    // Used for migration if it happens on Claim
    public BigInteger getWorkingBalance(Address user, boolean readonly) {
        BigInteger workingBalance = userWorkingBalance.at(dbKey).get(user);
        if (workingBalance != null) {
            return workingBalance;
        }

        workingBalance = loadCurrentSupply(user).get(BALANCE);
        if (!readonly) {
            setWorkingBalance(user, workingBalance);
        }

        return workingBalance;
    }

    // Used for migration if it happens on BalanceUpdate
    public BigInteger getWorkingBalance(Address user, BigInteger prevBalance, boolean readonly) {
        BigInteger workingBalance = userWorkingBalance.at(dbKey).get(user);
        if (workingBalance != null) {
            return workingBalance;
        }

        if (!readonly) {
            setWorkingBalance(user, prevBalance);
        }

        return prevBalance;
    }

    public BigInteger getWorkingBalance(Address user) {
        return userWorkingBalance.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    private void setWorkingBalance(Address user, BigInteger balance) {
        this.userWorkingBalance.at(dbKey).set(user, balance);
    }

    public Boolean getPrecomp() {
        return precomp.at(dbKey).getOrDefault(false);
    }

    public Integer getOffset() {
        return offset.at(dbKey).getOrDefault(0);
    }

    public BigInteger getTotalValue(BigInteger day) {
        return totalValue.at(dbKey).getOrDefault(day, BigInteger.ZERO);
    }

    public BigInteger getTotalDist(BigInteger day, boolean readonly) {
        DictDB<BigInteger, BigInteger> distAt = totalDist.at(dbKey);
        BigInteger dist = distAt.get(day);
        if (dist != null) {
            return dist;
        }

        dist = RewardsImpl.getTotalDist(getName(), day, readonly);
        if (!readonly) {
            distAt.set(day, dist);
        }

        return dist;
    }

    public BigInteger getTotalDist(BigInteger day) {
        return totalDist.at(dbKey).getOrDefault(day, BigInteger.ZERO);
    }

    public void setTotalDist(BigInteger day, BigInteger value) {
        totalDist.at(dbKey).set(day, value);
    }

    public BigInteger getDistPercent() {
        return distPercent.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public void setDistPercent(BigInteger distPercent) {
        this.distPercent.at(dbKey).set(distPercent);
    }

    public BigInteger getUserWeight(Address user) {
        return userWeight.at(dbKey).getOrDefault(user, BigInteger.ZERO);
    }

    public BigInteger getLastUpdateTimeUs() {
        return lastUpdateTimeUs.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public BigInteger getTotalWeight() {
        return totalWeight.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public BigInteger getTotalSupply() {
        return totalSupply.at(dbKey).getOrDefault(BigInteger.ZERO);
    }

    public Map<String, BigInteger> loadCurrentSupply(Address owner) {
        try {
            DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());
            return datasource.getBalanceAndSupply(getName(), owner);
        } catch (Exception e) {
            return Map.of("_totalSupply", BigInteger.ZERO,
                    "_balance", BigInteger.ZERO
            );
        }
    }

    public BigInteger updateSingleUserData(BigInteger currentTime, BigInteger prevTotalSupply, Address user,
                                           BigInteger prevBalance, boolean readOnlyContext) {
        BigInteger currentUserWeight = getUserWeight(user);
        BigInteger lastUpdateTimestamp = getLastUpdateTimeUs();

        BigInteger totalWeight = updateTotalWeight(lastUpdateTimestamp, currentTime, prevTotalSupply, readOnlyContext);

        if (currentUserWeight.equals(totalWeight)) {
            return BigInteger.ZERO;
        }

        BigInteger accruedRewards = BigInteger.ZERO;
        //  If the user's current weight is less than the total, update their weight and issue rewards
        if (prevBalance.compareTo(BigInteger.ZERO) > 0) {
            accruedRewards = computeUserRewards(prevBalance, totalWeight, currentUserWeight);
        }

        if (!readOnlyContext) {
            userWeight.at(dbKey).set(user, totalWeight);
        }

        return accruedRewards;
    }

    public void updateWorkingBalanceAndSupply(Address user, BalanceData balances) {
        BigInteger balance = balances.balance;
        BigInteger supply = balances.supply;
        Context.require(balance.compareTo(BigInteger.ZERO) >= 0);
        Context.require(supply.compareTo(BigInteger.ZERO) >= 0);

        BigInteger weight = RewardsImpl.boostWeight.get();
        BigInteger max = balance.multiply(EXA).divide(weight);

        BigInteger boost = BigInteger.ZERO;
        if (balances.boostedSupply.compareTo(BigInteger.ZERO) > 0 && balance.compareTo(BigInteger.ZERO) > 0) {
            boost = supply.multiply(balances.boostedBalance).multiply(EXA.subtract(weight)).divide(balances.boostedSupply).divide(weight);
        }

        BigInteger newWorkingBalance = balance.add(boost);
        newWorkingBalance = newWorkingBalance.min(max);

        BigInteger previousWorkingBalance = getWorkingBalance(user);
        BigInteger previousWorkingSupply = getWorkingSupply();

        BigInteger newTotalWorkingSupply =
                previousWorkingSupply.subtract(previousWorkingBalance).add(newWorkingBalance);

        setWorkingBalance(user, newWorkingBalance);
        setWorkingSupply(newTotalWorkingSupply);
    }

    private BigInteger computeTotalWeight(BigInteger previousTotalWeight,
                                          BigInteger emission,
                                          BigInteger totalSupply,
                                          BigInteger lastUpdateTime,
                                          BigInteger currentTime) {
        if (emission.equals(BigInteger.ZERO) || totalSupply.equals(BigInteger.ZERO)) {
            return previousTotalWeight;
        }

        BigInteger timeDelta = currentTime.subtract(lastUpdateTime);
        if (timeDelta.equals(BigInteger.ZERO)) {
            return previousTotalWeight;
        }

        BigInteger weightDelta =
                emission.multiply(timeDelta).multiply(EXA).divide(MICRO_SECONDS_IN_A_DAY).divide(totalSupply);

        return previousTotalWeight.add(weightDelta);
    }

    private BigInteger updateTotalWeight(BigInteger lastUpdateTimestamp, BigInteger currentTime,
                                         BigInteger totalSupply, boolean readOnlyContext) {

        BigInteger runningTotal = getTotalWeight();

        if (lastUpdateTimestamp.equals(BigInteger.ZERO)) {
            lastUpdateTimestamp = currentTime;
            if (!readOnlyContext) {
                lastUpdateTimeUs.at(dbKey).set(currentTime);
            }
        }

        if (currentTime.equals(lastUpdateTimestamp)) {
            return runningTotal;
        }

        // Emit rewards based on the time delta * reward rate
        BigInteger previousRewardsDay;
        BigInteger previousDayEndUs;

        while (lastUpdateTimestamp.compareTo(currentTime) < 0) {
            previousRewardsDay = lastUpdateTimestamp.divide(MICRO_SECONDS_IN_A_DAY);
            previousDayEndUs = previousRewardsDay.add(BigInteger.ONE).multiply(MICRO_SECONDS_IN_A_DAY);
            BigInteger endComputeTimestampUs = previousDayEndUs.min(currentTime);

            BigInteger emission = getTotalDist(previousRewardsDay, readOnlyContext);
            runningTotal = computeTotalWeight(runningTotal, emission, totalSupply, lastUpdateTimestamp,
                    endComputeTimestampUs);
            lastUpdateTimestamp = endComputeTimestampUs;
        }

        if (!readOnlyContext) {
            totalWeight.at(dbKey).set(runningTotal);
            lastUpdateTimeUs.at(dbKey).set(currentTime);
        }

        return runningTotal;
    }

    public BigInteger getValue() {
        DataSourceScoreInterface datasource = new DataSourceScoreInterface(getContractAddress());
        return datasource.getBnusdValue(getName());
    }

    public Map<String, Object> getDataAt(BigInteger day) {
        Map<String, Object> sourceData = new HashMap<>();
        sourceData.put("day", day);
        sourceData.put("contract_address", getContractAddress());
        // dist_percent is deprecated
        sourceData.put("dist_percent", getDistPercent());
        sourceData.put("workingSupply", getWorkingSupply());
        sourceData.put("total_value", getTotalValue(day));
        sourceData.put("total_dist", getTotalDist(day, true));

        return sourceData;
    }

    public Map<String, Object> getData() {
        BigInteger day = this.getDay();
        return getDataAt(day);
    }

    private BigInteger computeUserRewards(BigInteger prevUserBalance, BigInteger totalWeight, BigInteger userWeight) {
        BigInteger deltaWeight = totalWeight.subtract(userWeight);
        return deltaWeight.multiply(prevUserBalance).divide(EXA);
    }
}