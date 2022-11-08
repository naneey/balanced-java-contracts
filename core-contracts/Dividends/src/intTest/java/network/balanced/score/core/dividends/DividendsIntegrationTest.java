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

package network.balanced.score.core.dividends;

import foundation.icon.icx.KeyWallet;
import network.balanced.score.lib.structs.DistributionPercentage;
import network.balanced.score.lib.test.integration.Balanced;
import network.balanced.score.lib.test.integration.BalancedClient;
import network.balanced.score.lib.utils.Names;

import org.json.JSONObject;
import org.junit.jupiter.api.*;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;

import score.Address;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static network.balanced.score.lib.test.integration.BalancedUtils.*;
import static network.balanced.score.lib.utils.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DividendsIntegrationTest {
    static KeyWallet owner;
    static Balanced balanced;

    static BalancedClient alice;
    static BalancedClient bob;
    static BalancedClient charlie;
    static BalancedClient Dave;
    static BalancedClient Eve;
    static BalancedClient Ferry;
    static BalancedClient ownerClient;


    @BeforeAll
    static void setup() throws Exception {
        // deploying continuous dividends at first
        System.setProperty("Dividends", System.getProperty("dividends-continuous"));

        balanced = new Balanced();
        balanced.setupBalanced();

        alice = balanced.newClient();
        bob = balanced.newClient();
        charlie = balanced.newClient();
        Dave = balanced.newClient();
        Eve = balanced.newClient();
        Ferry = balanced.newClient();

        owner = balanced.owner;
        ownerClient = balanced.ownerClient;

        activateDividends();
    }

    public static void activateDividends() {
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        ownerClient.dividends.setDistributionActivationStatus(true);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

    }

    @Test
    @Order(1)
    void testName() throws Exception {
        BalancedClient balancedClient = balanced.newClient();
        activateDividends();
        assertEquals("Balanced Dividends", balancedClient.dividends.name());
    }

    @Test
    @Order(2)
    void testContinuousDividends() {
        /* test continuous rewards for dividends i.e. once continuous rewards is activated only staked baln will get
         the dividends */

        balanced.increaseDay(1);
        balanced.syncDistributions();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        // take loans
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(1000).multiply(BigInteger.TEN.pow(18)), "bnUSD",
                loanAmount, null, null);

        BigInteger amount = new BigInteger("500").multiply(BigInteger.TEN.pow(18));
        // create bnusd market

        ownerClient.staking.stakeICX(amount, Dave.getAddress(), null);
        ownerClient.staking.stakeICX(new BigInteger("50").multiply(BigInteger.TEN.pow(18)),
                Address.fromString(owner.getAddress().toString()), null);

        JSONObject data = new JSONObject();
        data.put("method", "_deposit");

        BigInteger lpAmount = BigInteger.valueOf(30).multiply(BigInteger.TEN.pow(18));
        balanced.increaseDay(1);
        balanced.syncDistributions();
        // claim rewards for the user
        ownerClient.rewards.claimRewards(null);

        // provides liquidity to baln/Sicx pool by owner
        ownerClient.baln.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.sicx.transfer(balanced.dex._address(), lpAmount, data.toString().getBytes());
        ownerClient.dex.add(balanced.baln._address(), balanced.sicx._address(), lpAmount, lpAmount, true);
        ownerClient.baln.transfer(Dave.getAddress(),
                BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18)), null);

        // stake balance by Dave
        Dave.baln.stake(lpAmount);

        String name = "BALN/sICX";
        BigInteger pid = ownerClient.dex.getPoolId(balanced.baln._address(), balanced.sicx._address());
        setMarketName(pid, name);

        // loans to create a dividends
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);

        BigInteger balnHolderPercentage = ownerClient.dividends.getDividendsPercentage().get("baln_holders");
        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        // Dividends for the BalnStaker
        BigInteger expectedDividendsForBalnStaker = fee.multiply(balnHolderPercentage).divide(EXA);

        Map<String, BigInteger> ownerDividends =
                ownerClient.dividends.getUnclaimedDividends(Address.fromString(owner.getAddress().toString()));
        Map<String, BigInteger> DaveDividends =
                ownerClient.dividends.getUnclaimedDividends(Dave.getAddress());
        BigInteger ownerDividendsBnusd = ownerDividends.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);
        BigInteger DaveDividendsBnusd = DaveDividends.getOrDefault(balanced.bnusd._address().toString(),
                BigInteger.ZERO);

        // LP provider(owner) should have zero dividends to claim after continuous rewards is activated
        assertEquals(ownerDividendsBnusd, BigInteger.ZERO);
        // As Dave staked baln , so Dave is eligible for dividends
        assertEquals(ownerDividendsBnusd.add(DaveDividendsBnusd), expectedDividendsForBalnStaker);
    }

    @Test
    @Order(3)
    void testBBaln_daofund() {
        /*
        If there are no supply of boosted baln even after bbaln day is started
        but there are some dividends received by dividends contract then,
        1. Daofund will get all the dividends .
        2. None of the user dividends will be increased.
         */
        createNewUserForBBaln();

        // contract is updated with bbaln changes in dividends
        byte[] contractData = getContractBytes(System.getProperty("java"));

        // Act
        ownerClient.governance.deployTo(balanced.dividends._address(), contractData, "[]");

        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();
        Address addressCharlie = charlie.getAddress();

        Map<String, BigInteger> unclaimedDividendsAlice = ownerClient.dividends.getUnclaimedDividends(addressAlice);
        Map<String, BigInteger> unclaimedDividendsBob = ownerClient.dividends.getUnclaimedDividends(addressBob);
        Map<String, BigInteger> unclaimedDividendsCharlie = ownerClient.dividends.getUnclaimedDividends(addressCharlie);

        ownerClient.governance.execute(createSingleTransaction(balanced.dividends._address(), "setBoostedBaln",
                 new JsonArray().add(createParameter(balanced.bBaln._address()))).toString());

        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));

        // Act
        BigInteger daoFundBalancePre = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);

        // loan taken after the update of the contract
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD"
                , loanAmount, null, null);
        BigInteger daoFundBalancePost = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        // dividends are sent to daofund directly as there are no boosted baln yet
        assertEquals(daoFundBalancePre.add(fee), daoFundBalancePost);
        // dividends shouldn't increase once bbalnDay is set unless there is some transaction
        assertEquals(unclaimedDividendsAlice, ownerClient.dividends.getUnclaimedDividends(addressAlice));
        assertEquals(unclaimedDividendsBob, ownerClient.dividends.getUnclaimedDividends(addressBob));

        // new user will have nothing unless he adds bbaln
        assertEquals(unclaimedDividendsCharlie, ownerClient.dividends.getUnclaimedDividends(addressCharlie));
    }

    @Test
    @Order(4)
    void testBBaln_lock() {
        /*
        1. Daofund doesn't get all the dividends once user starts locking baln token.
        2. User1 locks balance for few weeks and starts getting dividends.
        2. User2 doesn't lock balance and the unclaimed dividends remain same for few weeks.
         */
        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();

        BigInteger unclaimedDividendsBeforeBob =
                ownerClient.dividends.getUnclaimedDividends(addressBob).get(balanced.bnusd._address().toString());

        // user unstakes all the baln token
        alice.baln.stake(BigInteger.ZERO);
        BigInteger availableBalnBalance = alice.baln.availableBalanceOf(addressAlice);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // alice locks baln for 4 weeks
        alice.baln.transfer(alice.boostedBaln._address(), availableBalnBalance.divide(BigInteger.TWO), data.getBytes());

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger feePercent = hexObjectToBigInteger(ownerClient.loans.getParameters().get("origination fee"));
        BigInteger daoFundBalancePre = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger fee = loanAmount.multiply(feePercent).divide(POINTS);
        BigInteger unclaimedDividendsBeforeAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(balanced.bnusd._address().toString());

        // did tx to create a dividends
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsAfterAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(balanced.bnusd._address().toString());

        BigInteger daoFundBalancePost = ownerClient.bnUSD.balanceOf(balanced.daofund._address());
        BigInteger daoPercentage = ownerClient.dividends.getDividendsPercentage().get("daofund");
        BigInteger daoFee = fee.multiply(daoPercentage).divide(EXA);

        // daofund doesn't get all the dividends value once there is a supply in bbaln
        assertEquals(daoFundBalancePre.add(daoFee), daoFundBalancePost);

        // unclaimed dividends increases for Alice once the dividends is received by contract
        assertTrue(unclaimedDividendsAfterAlice.compareTo(unclaimedDividendsBeforeAlice) > 0);

        // day changes and creation of dividends
        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD", loanAmount, null, null);

        BigInteger unclaimedDividendsAfterBob =
                ownerClient.dividends.getUnclaimedDividends(addressBob).get(balanced.bnusd._address().toString());
        BigInteger newDividendsAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(balanced.bnusd._address().toString());

        /* as Bob is not migrated to bbaln , the unclaimed dividends
         remains same even after days */
        assertEquals(unclaimedDividendsAfterBob, unclaimedDividendsBeforeBob);

        /* dividends keeps on increasing for alice after dividends
         is received by contract */
        assertTrue(newDividendsAlice.compareTo(unclaimedDividendsAfterAlice) > 0);

        BigInteger bobBnusdBalanceBefore = ownerClient.bnUSD.balanceOf(addressBob);

        // bob claim dividends
        bob.dividends.claimDividends();

        BigInteger bobBnusdBalanceAfter = ownerClient.bnUSD.balanceOf(addressBob);
        BigInteger newUnclaimedDividendsBob =
                ownerClient.dividends.getUnclaimedDividends(addressBob).get(balanced.bnusd._address().toString());

        // unclaimedDividends goes to bob wallet
        assertEquals(bobBnusdBalanceAfter, bobBnusdBalanceBefore.add(unclaimedDividendsAfterBob));

        /* bob claims the dividends for baln stake after many days of bbaln start.
         once bob claims dividends the unclaimedDividends become 0 */
        assertEquals(newUnclaimedDividendsBob, BigInteger.ZERO);
    }

    @Test
    @Order(5)
    void testBBaln_claimOnly() {
        /* Eve claims the dividends after the contract is updated
        without unstaking staked baln token or without creating lock */
        Address addressEve = Eve.getAddress();
        BigInteger unclaimedDividendsBeforeEve =
                ownerClient.dividends.getUnclaimedDividends(addressEve).get(balanced.bnusd._address().toString());
        BigInteger bnusdBeforeEve = Eve.bnUSD.balanceOf(addressEve);
        Eve.dividends.claimDividends();
        BigInteger unclaimedDividendsAfterEve =
                ownerClient.dividends.getUnclaimedDividends(addressEve).get(balanced.bnusd._address().toString());
        BigInteger bnusdAfterEve = Eve.bnUSD.balanceOf(addressEve);
        // unclaimed dividends become 0 after claiming
        assertEquals(unclaimedDividendsAfterEve, BigInteger.ZERO);
        // unclaimed dividends should go to Eve's wallet after claiming
        assertEquals(bnusdBeforeEve.add(unclaimedDividendsBeforeEve), bnusdAfterEve);

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfterEve =
                ownerClient.dividends.getUnclaimedDividends(addressEve).get(balanced.bnusd._address().toString());

        // unclaimed dividends remains 0 for that user
        assertEquals(unclaimedDividendsAfterEve, BigInteger.ZERO);

    }

    @Test
    @Order(6)
    void testBBaln_claimAfterUnstake() {
        /* Ferry claims the dividends after unstaking baln token. */
        Address addressFerry = Ferry.getAddress();
        BigInteger unclaimedDividendsBeforeFerry =
                ownerClient.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());
        BigInteger bnusdBeforeFerry = Ferry.bnUSD.balanceOf(addressFerry);
        // Ferry unstakes baln token
        Ferry.baln.stake(BigInteger.ZERO);
        Ferry.dividends.claimDividends();
        BigInteger unclaimedDividendsAfterFerry =
                ownerClient.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());
        BigInteger bnusdAfterFerry = Eve.bnUSD.balanceOf(addressFerry);
        // unclaimed dividends become 0 after claiming
        assertEquals(unclaimedDividendsAfterFerry, BigInteger.ZERO);
        // unclaimed dividends should go to Eve's wallet after claiming
        assertEquals(bnusdBeforeFerry.add(unclaimedDividendsBeforeFerry), bnusdAfterFerry);

        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfterFerry =
                ownerClient.dividends.getUnclaimedDividends(addressFerry).get(balanced.bnusd._address().toString());

        // unclaimed dividends remains 0 for that user
        assertEquals(unclaimedDividendsAfterFerry, BigInteger.ZERO);

    }

    @Test
    @Order(7)
    void testBBaln_claim() {
        /*
        1. Alice claims the dividends and the expected dividends is sent to Alice wallet.
        2. After the claim , there will be dividends for Alice only if dividends is received by the contract.
        3. Multiple claim of dividends doesn't increase the balance.
         */
        Address addressAlice = alice.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        BigInteger unclaimedDividendsBeforeAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(ownerClient.bnUSD._address().toString());
        BigInteger bnusdBalanceBeforeAlice = ownerClient.bnUSD.balanceOf(addressAlice);

        // alice claim dividends
        alice.dividends.claimDividends();

        BigInteger newUnclaimedDividendsAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(ownerClient.bnUSD._address().toString());
        BigInteger bnusdAfterAlice = ownerClient.bnUSD.balanceOf(addressAlice);

        // claims second time
        alice.dividends.claimDividends();

        // bnusd in Alice wallet doesn't increase
        assertEquals(bnusdAfterAlice, ownerClient.bnUSD.balanceOf(addressAlice));

        BigInteger expectedBnusdAfterClaim = bnusdBalanceBeforeAlice.add(unclaimedDividendsBeforeAlice);

        // unclaimedDividends goes to user wallet
        assertEquals(bnusdAfterAlice, expectedBnusdAfterClaim);

        // once user claims dividends the unclaimedDividends become zero
        assertEquals(newUnclaimedDividendsAlice, BigInteger.ZERO);

        balanced.increaseDay(1);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        newUnclaimedDividendsAlice =
                ownerClient.dividends.getUnclaimedDividends(addressAlice).get(balanced.bnusd._address().toString());

        // unclaimed dividends have some value once dividends is received by contract
        assertTrue(newUnclaimedDividendsAlice.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    @Order(8)
    void testBBaln_newUser() {
        /*
        A new user comes and locks the baln and that user will be eligible to earn dividends
        anytime after that.
         */
        Address addressCharlie = charlie.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));
        BigInteger availableBalnBalanceCharlie = alice.baln.availableBalanceOf(addressCharlie);
        BigInteger WEEK_IN_MICRO_SECONDS = BigInteger.valueOf(7L).multiply(MICRO_SECONDS_IN_A_DAY);

        long unlockTime =
                (System.currentTimeMillis() * 1000) + (BigInteger.valueOf(4).multiply(WEEK_IN_MICRO_SECONDS)).longValue();
        String data = "{\"method\":\"createLock\",\"params\":{\"unlockTime\":" + unlockTime + "}}";

        // a new user will have 0 accumulated dividends
        assertEquals(alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString()), BigInteger.ZERO);

        // locks baln for 4 weeks
        charlie.baln.transfer(alice.boostedBaln._address(), availableBalnBalanceCharlie.divide(BigInteger.TWO),
                data.getBytes());

        // for dividends
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        BigInteger unclaimedDividendsBeforeCharlie =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString());
        BigInteger bnusdBalancePreCharlie = ownerClient.bnUSD.balanceOf(addressCharlie);

        // after CHarlie locks baln, he will start getting dividends
        assertTrue(unclaimedDividendsBeforeCharlie.compareTo(BigInteger.ZERO) > 0);

        charlie.dividends.claimDividends();

        BigInteger bnusdBalancePostCharlie = ownerClient.bnUSD.balanceOf(addressCharlie);

        assertEquals(bnusdBalancePostCharlie, bnusdBalancePreCharlie.add(unclaimedDividendsBeforeCharlie));

        // after claiming dividends unclaimed dividends will be zero unless dividends is received.
        assertEquals(alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString()), BigInteger.ZERO);

    }

    @Test
    @Order(9)
    void testBBaln_newUser_kicked() {
        /*
        A user starts getting less dividends once kicked.
         */
        Address addressCharlie = charlie.getAddress();
        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsBefore =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString());
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        BigInteger unclaimedDividendsAfter =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString());
        // checking if the user are getting same dividends everytime
        assertTrue(unclaimedDividendsAfter.subtract(unclaimedDividendsBefore.add(unclaimedDividendsBefore)).compareTo(BigInteger.valueOf(1)) <= 0);
        charlie.dividends.claimDividends();


        // charlie unclaimed dividends after claim is 0
        assertEquals(charlie.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString()), BigInteger.ZERO);

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsBefore =
                charlie.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString());
        charlie.dividends.claimDividends();

        alice.boostedBaln.kick(addressCharlie);
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
        unclaimedDividendsAfter =
                alice.dividends.getUnclaimedDividends(addressCharlie).get(ownerClient.bnUSD._address().toString());
        // user dividends is decreased once they are kicked
        assertTrue(unclaimedDividendsAfter.compareTo(unclaimedDividendsBefore) <= 0);
    }

    @Test
    @Order(10)
    void testRemoveCategories() {
        // test the removal of categories from dividends
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

        DistributionPercentage map = new DistributionPercentage();
        // firstly setting the baln_holders as 0 percentage
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("0");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("1000000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };

        // setting dividends category to 0 for baln_holders at first
        ownerClient.dividends.setDividendsCategoryPercentage(percentMap);

        // removing the categories
        ownerClient.dividends.removeDividendsCategory("baln_holders");
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        List<String> categories;
        categories = ownerClient.dividends.getDividendsCategories();
        assertEquals(1, categories.size());

    }

    @Test
    @Order(11)
    void testAddCategories() {
        // add new categories in dividends

        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());
        ownerClient.dividends.setDistributionActivationStatus(true);
        ownerClient.dividends.addDividendsCategory("baln_holders");
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());
        List<String> categories;
        categories = ownerClient.dividends.getDividendsCategories();
        assertEquals("baln_holders", categories.get(categories.size() - 1));
    }

    @Test
    @Order(12)
    void testChangeInPercentage() {

        balanced.increaseDay(1);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.ownerClient.getAddress());

        // verify the change in percentage
        DistributionPercentage map = new DistributionPercentage();
        map.recipient_name = "baln_holders";
        map.dist_percent = new BigInteger("900000000000000000");

        DistributionPercentage map2 = new DistributionPercentage();
        map2.recipient_name = "daofund";
        map2.dist_percent = new BigInteger("100000000000000000");

        DistributionPercentage[] percentMap = new DistributionPercentage[]{
                map, map2
        };


        // set new percentage of categories of dividends
        ownerClient.dividends.setDividendsCategoryPercentage(percentMap);
        ownerClient.governance.setAdmin(balanced.dividends._address(), balanced.governance._address());

        BigInteger daoBalanceBefore = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        BigInteger loanAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        // take loans
        ownerClient.loans.depositAndBorrow(BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18)), "bnUSD",
                loanAmount, null, null);

        BigInteger originationFees = BigInteger.valueOf(100);
        BigInteger dividendsBalance = (loanAmount.multiply(originationFees)).divide(BigInteger.valueOf(10000));
        Map<String, BigInteger> daoFundDividendsPercent = ownerClient.dividends.getDividendsPercentage();
        BigInteger dividendsToDao = daoFundDividendsPercent.get("daofund").multiply(dividendsBalance).divide(EXA);

        BigInteger daoBalanceAfter = ownerClient.bnUSD.balanceOf(balanced.daofund._address());

        assertEquals(daoBalanceAfter, daoBalanceBefore.add(dividendsToDao));
    }


    void createNewUserForBBaln() {
        // alice and bob stakes baln token

        Address addressAlice = alice.getAddress();
        Address addressBob = bob.getAddress();
        Address addressCharlie = charlie.getAddress();
        Address addressEve = Eve.getAddress();
        Address addressFerry = Ferry.getAddress();

        BigInteger loanAmount = BigInteger.valueOf(100).multiply(BigInteger.TEN.pow(18));
        BigInteger collateral = BigInteger.valueOf(500).multiply(BigInteger.TEN.pow(18));

        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);

        balanced.syncDistributions();
        balanced.increaseDay(10);
        for (int i = 0; i < 8; i++) {
            balanced.syncDistributions();
        }

        balanced.ownerClient.rewards.claimRewards(null);
        // sent baln token to two users
        balanced.ownerClient.baln.transfer(addressAlice, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(addressBob, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(addressCharlie, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(addressEve, collateral, new byte[0]);
        balanced.ownerClient.baln.transfer(addressFerry, collateral, new byte[0]);

        // staking baln token with multiple different users.
        BigInteger stakedAmount = BigInteger.valueOf(50).multiply(BigInteger.TEN.pow(18));
        alice.baln.stake(stakedAmount);
        bob.baln.stake(stakedAmount);
        Eve.baln.stake(stakedAmount);
        Ferry.baln.stake(stakedAmount);

        // loan taken to send some dividends to contract
        ownerClient.loans.depositAndBorrow(collateral, "bnUSD"
                , loanAmount, null, null);
    }

    void setMarketName(BigInteger poolID, String name) {
        JsonArray setMarketNameParameters = new JsonArray()
            .add(createParameter(poolID))
            .add(createParameter(name));

        JsonArray actions = new JsonArray()
            .add(createTransaction(balanced.dex._address(), "setMarketName", setMarketNameParameters));

            balanced.ownerClient.governance.execute(actions.toString());
    }

}
