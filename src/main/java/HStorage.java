

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.5.0.
 */
@SuppressWarnings("rawtypes")
public class HStorage extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b506000339080600181540180825580915050600190039060005260206000200160009091909190916101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff1602179055506104d0806100836000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80635f1e1acb1461004657806364cc7327146101015780637065cb48146101d0575b600080fd5b6100ff6004803603602081101561005c57600080fd5b810190808035906020019064010000000081111561007957600080fd5b82018360208201111561008b57600080fd5b803590602001918460018302840111640100000000831117156100ad57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f820116905080830192505050505050509192919290505050610214565b005b6101ba6004803603602081101561011757600080fd5b810190808035906020019064010000000081111561013457600080fd5b82018360208201111561014657600080fd5b8035906020019184600183028401116401000000008311171561016857600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600081840152601f19601f820116905080830192505050505050509192919290505050610323565b6040518082815260200191505060405180910390f35b610212600480360360208110156101e657600080fd5b81019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190505050610396565b005b6000805b6000805490508110156102a6576000818154811061023257fe5b9060005260206000200160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16141561029957600191506102a6565b8080600101915050610218565b50806102b157600080fd5b436001836040518082805190602001908083835b602083106102e857805182526020820191506020810190506020830392506102c5565b6001836020036101000a0380198251168184511680821785525050505050509050019150509081526020016040518091039020819055505050565b60006001826040518082805190602001908083835b6020831061035b5780518252602082019150602081019050602083039250610338565b6001836020036101000a0380198251168184511680821785525050505050509050019150509081526020016040518091039020549050919050565b6000805b60008054905081101561042857600081815481106103b457fe5b9060005260206000200160009054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16141561041b5760019150610428565b808060010191505061039a565b508061043357600080fd5b6000829080600181540180825580915050600190039060005260206000200160009091909190916101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550505056fea26469706673582212203d715042ae1b672c3ae456b5e408fb8e5b930d56e347f7f2d636a2471d243f2764736f6c63430007000033";

    public static final String FUNC_ADDOWNER = "addOwner";

    public static final String FUNC_RETRIEVE = "retrieve";

    public static final String FUNC_STORE_HASH = "store_hash";

    @Deprecated
    protected HStorage(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected HStorage(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected HStorage(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected HStorage(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<TransactionReceipt> addOwner(String newOwner) {
        final Function function = new Function(
                FUNC_ADDOWNER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, newOwner)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    public RemoteFunctionCall<BigInteger> retrieve(String jdh) {
        final Function function = new Function(FUNC_RETRIEVE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(jdh)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<TransactionReceipt> store_hash(String jdh) {
        final Function function = new Function(
                FUNC_STORE_HASH, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Utf8String(jdh)), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static HStorage load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new HStorage(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static HStorage load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new HStorage(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static HStorage load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new HStorage(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static HStorage load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new HStorage(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<HStorage> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(HStorage.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    public static RemoteCall<HStorage> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(HStorage.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<HStorage> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(HStorage.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<HStorage> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(HStorage.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }
}
