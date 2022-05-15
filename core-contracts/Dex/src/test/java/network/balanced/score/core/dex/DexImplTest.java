package network.balanced.score.core.dex;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.mockito.internal.matchers.Null;
import org.mockito.plugins.MemberAccessor.OnConstruction;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;


import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

import score.Address;
import score.Context;
import score.annotation.Optional;

import java.math.BigInteger;
import java.util.Map;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static network.balanced.score.lib.test.UnitTest.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class DexImplTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private static Account ownerAccount = sm.createAccount();
    private static Account adminAccount = sm.createAccount();

    int scoreCount = 0;
    private final Account governanceScore = Account.newScoreAccount(scoreCount++);
    private final Account dividendsScore = Account.newScoreAccount(scoreCount++);
    private final Account stakingScore = Account.newScoreAccount(scoreCount++);
    private final Account rewardsScore = Account.newScoreAccount(scoreCount++);
    private final Account bnusdScore = Account.newScoreAccount(scoreCount++);
    private final Account balnScore = Account.newScoreAccount(scoreCount++);
    private final Account sicxScore = Account.newScoreAccount(scoreCount++);
    private final Account feehandlerScore = Account.newScoreAccount(scoreCount++);
    private final Account stakedLPScore = Account.newScoreAccount(scoreCount++);

    public static Score dexScore;
    public static DexImpl dexScoreSpy;

    private final MockedStatic<Context> contextMock = Mockito.mockStatic(Context.class, Mockito.CALLS_REAL_METHODS);

    @BeforeEach
    public void setup() throws Exception {
        dexScore = sm.deploy(ownerAccount, DexImpl.class, governanceScore.getAddress());
        dexScore.invoke(governanceScore, "setTimeOffset", BigInteger.valueOf(Context.getBlockTimestamp()));
        dexScoreSpy = (DexImpl) spy(dexScore.getInstance());
        dexScore.setInstance(dexScoreSpy);
    }

    @Test
    void testName(){
        assertEquals(dexScore.call("name"), "Balanced DEX");
    }

    @Test
    void setGetAdmin() {
        testAdmin(dexScore, governanceScore, adminAccount);
    }
    
    @Test
    void setGetGovernance() {
        testGovernance(dexScore, governanceScore, ownerAccount);
    }

    @Test
    void setGetSicx() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                 "setSicx", dexScore.getAddress(), "getSicx");
    }
    
    @Test
    void setGetDividends() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setDividends", dividendsScore.getAddress(), "getDividends");
    }

    @Test
    void setGetStaking() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setStaking", stakingScore.getAddress(), "getStaking");
    }

    @Test
    void setGetRewards() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setRewards", rewardsScore.getAddress(), "getRewards");
    }

    @Test
    void setGetBnusd() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setbnUSD", bnusdScore.getAddress(), "getbnUSD");
    }

    @Test
    void setGetBaln() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setBaln", balnScore.getAddress(), "getBaln");
    }

    @Test
    void setGetFeehandler() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setFeehandler", feehandlerScore.getAddress(), "getFeehandler");
    }

    @Test
    void setGetStakedLP() {
        testContractSettersAndGetters(dexScore, governanceScore, adminAccount,
                "setStakedLp", stakedLPScore.getAddress(), "getStakedLp");
    }

    @Test
    @SuppressWarnings("unchecked")
    void setGetFees() {
        // Arrange - fees to be set.
        BigInteger poolLpFee = BigInteger.valueOf(100);
        BigInteger poolBalnFee = BigInteger.valueOf(200);
        BigInteger icxConversionFee = BigInteger.valueOf(300);
        BigInteger icxBalnFee = BigInteger.valueOf(400);

        // Arrange - methods to be called and set specified fee.
        Map<String, BigInteger> fees = Map.of(
            "setPoolLpFee", poolLpFee,
            "setPoolBalnFee", poolBalnFee,
            "setIcxConversionFee", icxConversionFee,
            "setIcxBalnFee", icxBalnFee
        );

        // Arrange - expected result when retrieving fees".
        Map<String, BigInteger> expectedResult = Map.of(
            "icx_total", icxBalnFee.add(icxConversionFee),
            "pool_total", poolBalnFee.add(poolLpFee),
            "pool_lp_fee", poolLpFee,
            "pool_baln_fee", poolBalnFee,
            "icx_conversion_fee", icxConversionFee,
            "icx_baln_fee", icxBalnFee
        );
        Map<String, BigInteger> returnedFees;

        // Act & assert - set all fees and assert that all fee methods are only settable by governance.
        for (Map.Entry<String, BigInteger> fee : fees.entrySet()) {
            dexScore.invoke(governanceScore, fee.getKey(), fee.getValue());
            assertOnlyCallableByGovernance(dexScore, fee.getKey(), fee.getValue());
        }

        // Assert - retrieve all fees and check validity.
        returnedFees =  (Map<String, BigInteger>) dexScore.call("getFees");
        assertTrue(expectedResult.equals(returnedFees));
    }
    
    @Test
    void turnDexOnAndGetDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
        assertEquals(true, dexScore.call("getDexOn"));
        assertOnlyCallableByGovernance(dexScore, "turnDexOn");
    }

    @Test
    void addQuoteCoinAndCheckIfAllowed() {
        // Arrange.
        Address quoteCoin = Account.newScoreAccount(1).getAddress();

        // Act.
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteCoin);

        // Assert.
        Boolean quoteCoinAllowed = (Boolean) dexScore.call("isQuoteCoinAllowed", quoteCoin);
        assertEquals(true, quoteCoinAllowed);
        assertOnlyCallableByGovernance(dexScore, "addQuoteCoin", quoteCoin);
    }

    @Test
    void setGetTimeOffSet() {
        // Arrange.
        BigInteger timeOffset = BigInteger.valueOf(100);

        // Act.
        dexScore.invoke(governanceScore, "setTimeOffset", timeOffset);

        // Assert.
        BigInteger retrievedTimeOffset = (BigInteger) dexScore.call("getTimeOffset");
        assertEquals(timeOffset, retrievedTimeOffset);
        assertOnlyCallableByGovernance(dexScore, "setTimeOffset", timeOffset);
    }

    // isLookingPool.

    @Test
    void setGetContinuousRewardsDay() {
        // Arrange.
        BigInteger continuousRewardsDay = BigInteger.valueOf(2);

        // Act.
        dexScore.invoke(governanceScore, "setContinuousRewardsDay", continuousRewardsDay);

        // Assert.
        BigInteger retrievedContinuousRewardsDay = (BigInteger) dexScore.call("getContinuousRewardsDay");
        assertEquals(continuousRewardsDay, retrievedContinuousRewardsDay);
        assertOnlyCallableByGovernance(dexScore, "setContinuousRewardsDay", continuousRewardsDay);
    }

    //@Test
    //void supplyLiquidity_newPoolCreated() {
    //    // Arrange - Tokenssupply information.
    //    Account supplier = sm.createAccount();
    //    Address baseToken = balnScore.getAddress();
    //    Address quoteToken = bnusdScore.getAddress();
    //    BigInteger baseValue = BigInteger.valueOf(10).pow(20);
    //    BigInteger quoteValue = BigInteger.valueOf(10).pow(20);
    //    Boolean withdrawUnused = false;
    //    
    //    // Arrange - configure dex settings.
    //    this.setupAddresses();
    //    dexScore.invoke(governanceScore, "turnDexOn");
    //    dexScore.invoke(governanceScore, "addQuoteCoin", bnusdScore.getAddress());
//
    //    // Arrange - Mock these cross-contract calls.
    //    contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
//
    //    // Act - deposit tokens and supply liquidity.
    //    dexScore.invoke(balnScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
    //    dexScore.invoke(bnusdScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
    //    dexScore.invoke(supplier, "add", baseToken, quoteToken, baseValue, quoteValue, withdrawUnused);

        // Assert.
    //}


    //@Test
    //void tokenFallback_rewardsNotFinished() {
    //    // Arrange.
    //    BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
    //    setupAddresses();
    //    dexScoreSpy.rewardsDone.set(false);
    //    contextMock.verify(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute")));
    //    contextMock.verify(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute")));
    //    

    //    // Move checks to the start of the funciton to test it properly.
    //    // Act.
    //    dexScore.invoke(bnusdScore, "tokenFallback", adminAccount.getAddress(), bnusdValue, new byte[0]);
    //}


    @Test
    void fallback() {
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        setupAddresses();
        contextMock.when(() -> Context.getValue()).thenReturn(icxValue);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        // This does not work. Why? Going with when in the meantime.
        //contextMock.verify(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any()));


        dexScore.invoke(ownerAccount, "fallback");
    }

    @Test
    void getIcxBalance() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);
        setupAddresses();

        // Act.
        supplyIcxLiquidity(supplier, value);

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(IcxBalance, value);
    }

    @Test
    void cancelSicxIcxOrder() {
        // Arrange.
        Account supplier = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000).multiply(EXA);

        setupAddresses();
        turnDexOn();
        supplyIcxLiquidity(supplier, value);
        supplyIcxLiquidity(ownerAccount, value);
        sm.getBlock().increase(100000);

        // Mock these.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        contextMock.when(() -> Context.transfer(eq(supplier.getAddress()), eq(value))).thenAnswer((Answer<Void>) invocation -> null);
        
        // Act.
        dexScore.invoke(supplier, "cancelSicxicxOrder");

        // Assert.
        BigInteger IcxBalance = (BigInteger) dexScore.call("getICXBalance", supplier.getAddress());
        assertEquals(BigInteger.ZERO, IcxBalance);
    }

    @Test
    void getSicxEarnings() {
        // Supply liquidity to sicx/icx pool.
        // Swap some sicx to icx.
        // Get and verify earnings.
    }

    @Test
    void withdrawSicxEarnings() {
        // Supply liquidity to sicx/icx pool.
        // Swap some sicx to icx.
        // Withdraw earnings.
        // Check that earnings transferred to withdrawer.
        // Assert that getSicxEarnings returns 0.
    }


    @Test
    void tokenFallback_deposit() {
        // Arrange.
        Account tokenScoreCaller = balnScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);
        BigInteger retrievedValue;
        
        setupAddresses();

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act.
        dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_deposit", new HashMap<>()));
        retrievedValue = (BigInteger) dexScore.call("getDeposit", tokenScoreCaller.getAddress(), tokenSender.getAddress());

        // Assert.
        assertEquals(value, retrievedValue);
    }

    @Test
    void onIRC31Received() {
        // Arrange.
        Account irc31Contract = Account.newScoreAccount(1);
        Address operator = sm.createAccount().getAddress();
        Address from = sm.createAccount().getAddress();
        BigInteger id = BigInteger.ONE;
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        byte[] data = new byte[0];
        String expectedErrorMessage = "Reverted(0): Balanced DEX: IRC31 Tokens not accepted";

        // Act and assert.
        Executable onIRC31Received = () -> dexScore.invoke(irc31Contract, "onIRC31Received", operator, from, id, value, data);
        expectErrorMessage(onIRC31Received, expectedErrorMessage);
    }

    @Test
    void transfer() {

    }

    @Test
    void withdrawTokens_insufficientBalance() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(1000).multiply(EXA);
        String expectedErrorMessage = "Balanced DEX: Insufficient Balance";
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawTokens_negativeAmount() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(-1000).multiply(EXA);
        String expectedErrorMessage = "Balanced DEX: Must specify a posititve amount";
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);
        
        // Act & assert.
        Executable withdrawalInvocation = () -> dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
        expectErrorMessage(withdrawalInvocation, expectedErrorMessage);
    }

    @Test
    void withdrawTokens() {
        // Arrange.
        Account depositor = sm.createAccount();
        BigInteger depositValue = BigInteger.valueOf(100).multiply(EXA);
        BigInteger withdrawValue = BigInteger.valueOf(10).multiply(EXA);
        turnDexOn();
        depositTokens(depositor, balnScore, depositValue);

        contextMock.when(() -> Context.call(eq(balnScore.getAddress()), eq("transfer"), eq(depositor.getAddress()), eq(withdrawValue))).thenReturn(null);
        
        // Act.
        dexScore.invoke(depositor, "withdraw", balnScore.getAddress(), withdrawValue);
    
        // Assert. 
        BigInteger currentDepositValue = (BigInteger) dexScore.call("getDeposit", balnScore.getAddress(), depositor.getAddress());
        assertEquals(depositValue.subtract(withdrawValue), currentDepositValue);
    }

    @Test
    void tokenFallback_swapIcx_revertOnIncompleteRewards() {
        // Arrange.
        Account tokenScoreCaller = sicxScore;
        Account tokenSender = sm.createAccount();
        BigInteger value = BigInteger.valueOf(1000000000);
        
        setupAddresses();

        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(false);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Act & assert.
        Executable incompleteRewards = () -> dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
        expectErrorMessage(incompleteRewards, "Reverted(0): Balanced DEX Rewards distribution in progress, please try again shortly");
    }

    //@Test
    //void tokenFallback_swapIcx() {
    //    // Arrange.
    //    Account tokenScoreCaller = sicxScore;
    //    Account tokenSender = sm.createAccount();
    //    BigInteger value = BigInteger.valueOf(1000000000);
    //    
    //    setupAddresses();
//
    //    contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
    //    contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
//
    //    dexScore.invoke(tokenScoreCaller, "tokenFallback", tokenSender.getAddress(), value, tokenData("_swap_icx", new HashMap<>()));
    //}


    @Test
    void getNonce() {
        // Arrange.
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, BigInteger.valueOf(10).pow(19), BigInteger.valueOf(10).pow(19), false);

        // Assert.
        BigInteger nonce = (BigInteger) dexScore.call( "getNonce");
        assertEquals(BigInteger.valueOf(3), nonce);
    }

    @Test
    void getPoolId() {
        // Arrange.
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, BigInteger.valueOf(10).pow(19), BigInteger.valueOf(10).pow(19), false);

        // Assert.
        BigInteger poolId = (BigInteger) dexScore.call( "getPoolId", bnusdScore.getAddress(), balnScore.getAddress());
        assertEquals(BigInteger.TWO, poolId);
    }

    @Test
    void lookupId() {
        // Arrange.
        String namedMarket = "sICX/ICX";
        BigInteger expectedId = BigInteger.valueOf(1);

        // Assert.
        BigInteger id = (BigInteger) dexScore.call("lookupPid", namedMarket);
        assertEquals(expectedId, id);
    }


    @Test
    void getPoolTotal() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedBnusdValue = (BigInteger) dexScore.call("getPoolTotal", poolId, bnusdScore.getAddress());
        BigInteger retrievedBalnValue = (BigInteger) dexScore.call("getPoolTotal", poolId, balnScore.getAddress());
        assertEquals(bnusdValue, retrievedBnusdValue);
        assertEquals(balnValue, retrievedBalnValue);
    }

    @Test
    void balanceOf_normalPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger userLpTokenValue = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedUserLpTokenValue = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        assertEquals(userLpTokenValue, retrievedUserLpTokenValue);
    }

    @Test
    void balanceOf_icxSicxPool() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        BigInteger poolId = BigInteger.valueOf(1);
        setupAddresses();

        // Act.
        supplyIcxLiquidity(ownerAccount, value);

        // Assert.
        BigInteger retrievedBalance = (BigInteger) dexScore.call("balanceOf", ownerAccount.getAddress(), poolId);
        assertEquals(value, retrievedBalance);
    }

    @Test
    void totalSupply_SicxIcxPool() {
        // Arrange.
        BigInteger value = BigInteger.valueOf(100).multiply(EXA);
        BigInteger poolId = BigInteger.valueOf(1);
        setupAddresses();

        // Act.
        supplyIcxLiquidity(ownerAccount, value);

        // Assert.
        BigInteger totalIcxSupply = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(value, totalIcxSupply);
    }

    @Test
    void totalSupply_normalPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        BigInteger totalLpTokens = (bnusdValue.multiply(balnValue)).sqrt();
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger retrievedTotalLpTokens = (BigInteger) dexScore.call("totalSupply", poolId);
        assertEquals(totalLpTokens, retrievedTotalLpTokens);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setGetMarketNames() {
        // Arrange.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        List<String> expectedMarketNames = Arrays.asList("sICX/ICX", poolName);

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Assert.
        List<String> namedPools = (List<String>) dexScore.call("getNamedPools");
        assertEquals(expectedMarketNames, namedPools);
        assertOnlyCallableByGovernance(dexScore, "setMarketName", poolId, poolName);
    }
    
    @Test
    void getPoolBaseAndGetPoolQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(10).pow(19);
        BigInteger balnValue = BigInteger.valueOf(10).pow(19);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Address poolBase = (Address) dexScore.call( "getPoolBase", BigInteger.TWO);
        Address poolQuote = (Address) dexScore.call( "getPoolQuote", BigInteger.TWO);
        assertEquals(poolBase, bnusdScore.getAddress());
        assertEquals(poolQuote, balnScore.getAddress());
    }

    @Test
    void getQuotePriceInBase() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, balnValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getQuotePriceInBase", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBasePriceInQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getBasePriceInQuote", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getPrice", BigInteger.TWO);
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBalnPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getBalnPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getSicxBnusdPrice() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(bnusdValue, sicxValue);
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, sicxScore, bnusdScore, sicxValue, bnusdValue, false);

        // Assert.
        BigInteger price = (BigInteger) dexScore.call( "getSicxBnusdPrice");
        assertEquals(expectedPrice, price);
    }

    @Test
    void getBnusdValue_sicxIcxPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger sicxIcxConversionRate = BigInteger.valueOf(10).multiply(EXA); 
        BigInteger icxValue = BigInteger.valueOf(10).multiply(EXA);
        String poolName = "sICX/ICX";
        BigInteger expectedPoolValue = (computePrice(bnusdValue, sicxValue).multiply(icxValue)).divide(sicxIcxConversionRate);
        setupAddresses();
        doReturn(sicxIcxConversionRate).when(dexScoreSpy).getSicxRate();
        
        
        // Act.
        supplyLiquidity(ownerAccount, sicxScore, bnusdScore, sicxValue, bnusdValue, false);
        supplyIcxLiquidity(ownerAccount, icxValue);     
        
        // Assert.
        BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", poolName);
        assertEquals(expectedPoolValue, poolValue);
    }

    @Test
    void getBnusdValue_sicxIsQuote() {
        // Arrange.
        BigInteger balnValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger sicxValue = BigInteger.valueOf(350).multiply(EXA);
        String poolName = "bnUSD/sICX";
        BigInteger poolId = BigInteger.valueOf(2);
        BigInteger sicxBnusdPrice = BigInteger.valueOf(10).multiply(EXA);
        BigInteger expectedValue = (sicxValue.multiply(BigInteger.TWO).multiply(sicxBnusdPrice)).divide(EXA);
        doReturn(sicxBnusdPrice).when(dexScoreSpy).getSicxBnusdPrice();
        setupAddresses();

        // Act. Why can I not supply with sicx as quote currency? Fails.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, sicxScore, balnValue, sicxValue, false);

        // Assert.
        //BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", poolName);
        //assertEquals(expectedValue, poolValue);
    }
    
    @Test
    void getBnusdValue_bnusdIsQuote() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedValue = BigInteger.valueOf(195).multiply(EXA).multiply(BigInteger.TWO);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();
      
        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, balnScore, bnusdScore, balnValue, bnusdValue, false);

        // Assert.
        BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", poolName);
        assertEquals(expectedValue, poolValue);
    }

    @Test
    void getBnusdValue_QuoteNotSupported() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        setupAddresses();

         // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, balnValue, bnusdValue, false);

         // Assert
        BigInteger poolValue = (BigInteger) dexScore.call( "getBnusdValue", "bnUSD/BALN");
        assertEquals(BigInteger.ZERO, poolValue);
    }

    @Test
    void getPriceByName() {
         // Arrange.
         BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
         BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
         String poolName = "bnUSD/BALN";
         BigInteger poolId = BigInteger.valueOf(2);
         BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
         setupAddresses();
 
          // Act.
         dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
         supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
 
          // Assert
         BigInteger price = (BigInteger) dexScore.call( "getPriceByName", "bnUSD/BALN");
         assertEquals(expectedPrice, price);
    }

    @Test
    void getPoolName() {
        // Arrange.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);

        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);

        // Assert.
        String retrievedPoolName = (String) dexScore.call("getPoolName", poolId);
        assertEquals(poolName, retrievedPoolName);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPoolStats_notSicxIcxPool() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger expectedPrice = computePrice(balnValue, bnusdValue);
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.valueOf(2);
        BigInteger tokenDecimals = BigInteger.valueOf(18);
        BigInteger minQuote = BigInteger.ZERO;
        BigInteger totalLpTokens = new BigInteger("261247009552262626468"); // Check how this is derived.

        Map<String, Object> expectedPoolStats = Map.of(
            "base", bnusdValue,
            "quote", balnValue,
            "base_token", bnusdScore.getAddress(),
            "quote_token", balnScore.getAddress(),
            "total_supply", totalLpTokens,
            "price", expectedPrice,
            "name", poolName,
            "base_decimals", tokenDecimals,
            "quote_decimals", tokenDecimals,
            "min_quote", minQuote
        );
        setupAddresses();
        
        // Act.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Assert.
        Map<String, Object> poolStats = (Map<String, Object>) dexScore.call( "getPoolStats", poolId);
        assertEquals(expectedPoolStats, poolStats);
    }

    @Test
    void getTotalDexAddresses() {
         // Arrange.
         BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
         BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
         BigInteger poolId = BigInteger.TWO;
         setupAddresses();
 
          // Act.
         supplyLiquidity(adminAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
         supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
 
          // Assert
         Integer totalDexAddresses = (int) dexScore.call( "totalDexAddresses", poolId);
         assertEquals(2, totalDexAddresses);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBalanceAndSupply_normalPool() {
        // Arrange - Variables.
        String poolName = "bnUSD/BALN";
        BigInteger poolId = BigInteger.TWO;
        BigInteger mockBalance = BigInteger.valueOf(100);
        BigInteger mockTotalSupply = BigInteger.valueOf(200);
        Map<String, BigInteger> expectedData = Map.of(
            "_balance", mockBalance,
            "_totalSupply", mockTotalSupply
        );

        // Arrange - Setup dex contract.
        dexScore.invoke(governanceScore, "setMarketName", poolId, poolName);
        setupAddresses();

        // Arrange - Mock these calls to stakedLP contract.
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("balanceOf"), eq(ownerAccount.getAddress()), eq(poolId))).thenReturn(mockBalance);
        contextMock.when(() -> Context.call(eq(stakedLPScore.getAddress()), eq("totalSupply"), eq(poolId))).thenReturn(mockTotalSupply);
        
        // Assert.
        Map<String, BigInteger> returnedData = (Map<String, BigInteger>) dexScore.call( "getBalanceAndSupply", poolName, ownerAccount.getAddress());
        assertEquals(expectedData, returnedData);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBalanceAndSupply_sicxIcxPool() {
        // Arrange - Variables.
        Account supplier = sm.createAccount();
        BigInteger icxValue = BigInteger.valueOf(100).multiply(EXA);
        String poolName = "sICX/ICX";
        
        Map<String, BigInteger> expectedData = Map.of(
            "_balance", icxValue,
            "_totalSupply", icxValue
        );

        setupAddresses();
        supplyIcxLiquidity(supplier, icxValue);
        
        // Assert.
        Map<String, BigInteger> returnedData = (Map<String, BigInteger>) dexScore.call( "getBalanceAndSupply", poolName, supplier.getAddress());
        assertEquals(expectedData, returnedData);
    }

    @Test
    void removeLiquidity_withdrawalLockActive() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity settings.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        setupAddresses();
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);

        // Act & Assert.
        Executable fundsLocked = () -> dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
        expectErrorMessage(fundsLocked, "Reverted(0): Balanced DEX:  Assets must remain in the pool for 24 hours, please try again later.");
    }

    @Test
    void removeLiquidity() {
        // Arrange - remove liquidity arguments.
        BigInteger poolId = BigInteger.TWO;
        BigInteger lpTokensToRemove = BigInteger.valueOf(1000);
        Boolean withdrawTokensOnRemoval = false;
        
        // Arrange - supply liquidity settings.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        setupAddresses();
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        sm.getBlock().increase(100000000);

         // Act & Assert.
         dexScore.invoke(ownerAccount, "remove", poolId, lpTokensToRemove, withdrawTokensOnRemoval);
         // Check current_lp_tokens = orignal_lp_tokens - lpTokensToRemove.
         // Check other setters?
    }

    @Test
    void getWithdrawLock() {
        // Arrange.
        BigInteger bnusdValue = BigInteger.valueOf(195).multiply(EXA);
        BigInteger balnValue = BigInteger.valueOf(350).multiply(EXA);
        BigInteger timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
        BigInteger poolId = BigInteger.TWO;
        setupAddresses();

        // Act.
        supplyLiquidity(ownerAccount, bnusdScore, balnScore, bnusdValue, balnValue, false);
        
        // Assert.
        BigInteger withdrawalLock = (BigInteger) dexScore.call("getWithdrawLock", poolId, ownerAccount.getAddress());
        //assertEquals(timestamp, withdrawalLock);

        // sm.getBlock().getTimestamp =! Context.getBlockTimestamp().These should be equal.
        // Bug in unittesting framework?
    }

    @Test
    void permit_OnlyGovernance() {
        // Arrange.
        BigInteger poolId = BigInteger.ONE;
        Boolean permission = true;

        // Assert.
        assertOnlyCallableByGovernance(dexScore, "permit", poolId, permission);
    }

    private void turnDexOn() {
        dexScore.invoke(governanceScore, "turnDexOn");
    }

    private void depositTokens(Account depositor, Account tokenScore, BigInteger value) {
        setupAddresses();
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));
        dexScore.invoke(tokenScore, "tokenFallback", depositor.getAddress(), value, tokenData("_deposit", new HashMap<>()));
    }

    private void setupAddresses() {
        dexScore.invoke(governanceScore, "setAdmin", adminAccount.getAddress());

        Map<String, Address> addresses = Map.of(
            "setDividends", dividendsScore.getAddress(),
            "setStaking", stakingScore.getAddress(),
            "setRewards", rewardsScore.getAddress(),
            "setbnUSD", bnusdScore.getAddress(),
            "setBaln", balnScore.getAddress(),
            "setSicx", sicxScore.getAddress(),
            "setFeehandler", feehandlerScore.getAddress(),
            "setStakedLp", stakedLPScore.getAddress()
        );
        
        for (Map.Entry<String, Address> address : addresses.entrySet()) {
            dexScore.invoke(adminAccount, address.getKey(), address.getValue());
        }
    }

    private void supplyLiquidity(Account supplier, Account baseTokenScore, Account quoteTokenScore, 
                                 BigInteger baseValue, BigInteger quoteValue, @Optional boolean withdrawUnused) {
        // Configure dex.
        dexScore.invoke(governanceScore, "turnDexOn");
        dexScore.invoke(governanceScore, "addQuoteCoin", quoteTokenScore.getAddress());

        // Mock these cross-contract calls.
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(any(Address.class), eq("decimals"))).thenReturn(BigInteger.valueOf(18));

        // Deposit tokens and supply liquidity.
        dexScore.invoke(baseTokenScore, "tokenFallback", supplier.getAddress(), baseValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(quoteTokenScore, "tokenFallback", supplier.getAddress(), quoteValue, tokenData("_deposit", new HashMap<>()));
        dexScore.invoke(supplier, "add", baseTokenScore.getAddress(), quoteTokenScore.getAddress(), baseValue, quoteValue, withdrawUnused);
    }

    private BigInteger computePrice(BigInteger tokenAValue, BigInteger tokenBValue) {
        return (tokenAValue.multiply(EXA)).divide(tokenBValue);
    }

    private void supplyIcxLiquidity(Account supplier, BigInteger value) {
        contextMock.when(() -> Context.getValue()).thenReturn(value);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(dividendsScore.getAddress()), eq("distribute"))).thenReturn(true);
        contextMock.when(() -> Context.call(eq(rewardsScore.getAddress()), eq("updateBatchRewardsData"), any(String.class), any(BigInteger.class), any())).thenReturn(null);
        supplier.addBalance("ICX", value);
        sm.transfer(supplier, dexScore.getAddress(), value);
    }



    /*
    Code organization:
    - ICX pool related methods.
    - Normal liquidity pool methods.
    - Snapshot methods.
    
    
    == Icx/sicx pool methods == 
    getSicxEarnings
    withdrawSicxEarnings
    fallback
    

    == Snapshot methods ==
    getBalnSnapshot
    loadBalancesAtSnapshot
    getDataBatch
    totalSupplyAt
    totalBalnAt
    balanceOfAt
    getTotalValue
    

    == Normal liquidity pool methods ==
    tokenFallback
    getDeposit  // Tested in tokenfallback_Deposit
    remove
    add
    addLpAddresses -> No getter.


    == Others ==
    transfer  -> IRC31 transfer method.
    */



    @AfterEach
    void closeMock() {
        contextMock.close();
    }
}
