package network.balanced.score.lib.utils;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.Address;
import score.annotation.External;
import scorex.util.ArrayList;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EnumerableSetDBTest extends TestBase {

    public static final BigInteger ZERO_ADDRESS = BigInteger.ZERO;
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    //address
    private final static Address address1 = sm.createAccount().getAddress();
    private final static Address address2 = sm.createAccount().getAddress();
    private final static Address address3 = sm.createAccount().getAddress();
    private final static Address address4 = sm.createAccount().getAddress();
    private final static Address address5 = sm.createAccount().getAddress();

    private static Score dummyScore;

    public static class DummyScore  {

        EnumerableSetDB<Address> enumerableSet = new EnumerableSetDB<>("enumerable_set", Address.class);
        public DummyScore() {

        }
        public void   setAddresses(ArrayList<Address > addresses){
            for (Address address: addresses) {
                enumerableSet.add(address);
            }
        }

        public int indexOf(Address address){
            return enumerableSet.indexOf(address);
        }

        public int length(){
            return enumerableSet.length();
        }

        public List<Address> getInRange(BigInteger from, BigInteger to){
            return enumerableSet.range(from, to);
        }

        public Boolean contains(Address address){
            return enumerableSet.contains(address);
        }

        public Address remove(Address address){
            return enumerableSet.remove(address);
        }

        public Address at(int pos){
            return enumerableSet.at(pos);
        }

    }


    @BeforeAll
    public static void setup() throws Exception {
        dummyScore = sm.deploy(owner, DummyScore.class);
    }

    @Test
    public void testSetGetIterableDictDB(){

        List<Address> addresses = new ArrayList<>();
        addresses.add(address1);
        addresses.add(address2);
        addresses.add(address3);
        addresses.add(address4);
        addresses.add(address5);


        dummyScore.invoke(owner, "setAddresses", addresses);

        Integer length =  (Integer) dummyScore.call("length");

        assertEquals(5, length);

        List<Address> addressList =  (List<Address>) dummyScore.call("getInRange", BigInteger.ZERO, BigInteger.valueOf(length));

        assertTrue(addressList.contains(address1));
        assertTrue(addressList.contains(address2));
        assertTrue(addressList.contains(address3));
        assertTrue(addressList.contains(address4));
        assertTrue(addressList.contains(address5));

        assertEquals(address3, dummyScore.call("at", 2));


        dummyScore.invoke(owner, "remove", address3);

        assertFalse((Boolean)dummyScore.call("contains", address3));

    }

}
