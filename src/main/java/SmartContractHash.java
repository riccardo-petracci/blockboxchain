import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.StaticGasProvider;

public class SmartContractHash 
{
	private final static String ENDPOINT = Config.getEnvVariable("BLOCKCHAIN_RPC_URL");
	private final static String PRIVATE_KEY = Config.getEnvVariable("BLOCKCHAIN_PRIVATE_KEY");
	private final static BigInteger GAS_LIMIT = BigInteger.valueOf(3000000);
	private final static BigInteger GAS_PRICE = BigInteger.valueOf(20000000000L);

		private String address;
		private String credentials;
		private String contract;
		private BigInteger gaslimit;
		private BigInteger gasprice;
		
		private Web3j web3jobj = null;
		private Credentials ethCREDENTIALS = null;
		private TransactionManager txManager = null;
		private ContractGasProvider contractGasProvider = null;
		private HStorage HSC = null;
	    
		
//		public static void main(String[] args) {
//			SmartContractHash SCH = new SmartContractHash(ENDPOINT, PRIVATE_KEY, GAS_LIMIT, GAS_PRICE );
//			String receipt = SCH.deployContract();
//			System.out.println("Deploy contract successfull, receipt:\n" + receipt);
//		}

	public static String main() {
		SmartContractHash SCH = new SmartContractHash(ENDPOINT, PRIVATE_KEY, GAS_LIMIT, GAS_PRICE );
		String receipt = SCH.deployContract();
		System.out.println("Deploy contract successfull, receipt:\n" + receipt);
		return receipt;
	}
		/**
		 * Costruttore per un contratto già deployato
		 *
		 * @param _address indirizzo server RPC
		 * @param _credentials chiave privata dell'account che esegue le call allo smart contract
		 * @param _contract indirizzo del contratto
		 * @param _gaslimit gas limit
		 * @param _gasprice gas price
		 */
		public SmartContractHash(String _address, String _credentials, String _contract, BigInteger _gaslimit, BigInteger  _gasprice)
		{
			address = _address;
			credentials = _credentials;
			contract = _contract;
			gaslimit = _gaslimit;
			gasprice = _gasprice;
			
			web3jobj =  Web3j.build(new HttpService(address));
			ethCREDENTIALS = Credentials.create(credentials);
			txManager = new RawTransactionManager(web3jobj, ethCREDENTIALS);
			contractGasProvider = new StaticGasProvider(gasprice, gaslimit);
			HSC = HStorage.load(contract, web3jobj, ethCREDENTIALS, contractGasProvider);
			
		}
		
		/**
		 * Costruttore per un contratto che andrà deployato successivamente
		 * 
		 * @param _address server RPC
		 * @param _credentials chiave privata dell'account che esegue le call allo smart contract
		 * @param _gaslimit gas limit
		 * @param _gasprice gas price
		 */
		public SmartContractHash(String _address, String _credentials, BigInteger _gaslimit, BigInteger  _gasprice)
		{
			address = _address;
			credentials = _credentials;
			gaslimit = _gaslimit;
			gasprice = _gasprice;
			
			web3jobj =  Web3j.build(new HttpService(address));
			ethCREDENTIALS = Credentials.create(credentials);
			txManager = new RawTransactionManager(web3jobj, ethCREDENTIALS);
			contractGasProvider = new StaticGasProvider(gasprice, gaslimit);
		}

	public static String getEndPoint() {
			return ENDPOINT;
	}
	public static String getPrivateKey() {
			return PRIVATE_KEY;
	}

	/**
		 * Esegue una call allo smart contract e salva l'hash passato come parametro
		 *  
		 * @param hash
		 * @return recepit della transazione
		 */
		public String storeHash(String hash)
		{
			TransactionReceipt transactionReceipt = null;
						
			try 
			{
				transactionReceipt = HSC.store_hash(hash).send();
			}
			catch (Exception e) 
			{
				System.out.println("Error storing value");
				e.printStackTrace();
			}	
			
			return transactionReceipt.toString();
		}
		
		/**
		 * Esegue una call allo smart contract per controllare che l'hash passato come parametro sia stato effettivamente salvato in precedenza

		 * @param hash
		 * @return numero del blocco in cui è stata eseguita la transazione per salvare l'hash specificato, consultabile successivamente in qualsiasi block explorer
		 */
		public int checkHash(String hash)
		{
			int blockNumber = 0;
						
			try
			{
				blockNumber = HSC.retrieve(hash).send().intValue();
			}
			catch (Exception e) 
			{
				System.out.println("Error getting value");
				e.printStackTrace();
			}
			
			return blockNumber;
		}
		
		/**
		 * Metodo che permette di deployare il contratto
		 * 
		 * @return indirizzo del contratto
		 */
		public String deployContract()
		{
			try 
			{
				HSC = HStorage.deploy(web3jobj, txManager, contractGasProvider).send();
				contract = HSC.getContractAddress();
				HSC = HStorage.load(contract, web3jobj, ethCREDENTIALS, contractGasProvider);
			}
			catch (Exception e) 
			{
				System.out.println("Error deploying contract");
				e.printStackTrace();
			}
			return contract;
		}

		
		/**
		 * metodo statico che permette di generare l'MD5 della stringa data in input
		 * 
		 * @param input stringa da condificare
		 * @return condifica in MD5
		 */
		public static String generateMD5(String input)
		{	
			try 
			{
			    MessageDigest md = MessageDigest.getInstance("MD5");
	            byte[] messageDigest = md.digest(input.getBytes());
	            BigInteger no = new BigInteger(1, messageDigest);
	            String hashtext = no.toString(16);
	            while (hashtext.length() < 32) 
	            {
	                hashtext = "0" + hashtext;
	            }
	            return hashtext;
	        }
	         catch (NoSuchAlgorithmException e) 
			{
			    System.out.println("Error generating MD5");
	            throw new RuntimeException(e);
	        }
		}
		
		public String getAddress() 
		{
			return address;
		}

		public void setAddress(String address) 
		{
			this.address = address;
			web3jobj =  Web3j.build(new HttpService(address));
			txManager = new RawTransactionManager(web3jobj, ethCREDENTIALS);
		}

		public String getCredentials() 
		{
			return credentials;
		}

		public void setCredentials(String credentials) 
		{
			this.credentials = credentials;
			ethCREDENTIALS = Credentials.create(credentials);
			txManager = new RawTransactionManager(web3jobj, ethCREDENTIALS);
		}

		public String getContract() 
		{
			return contract;
		}

		public void setContract(String contract) 
		{
			this.contract = contract;
			HSC = HStorage.load(contract, web3jobj, ethCREDENTIALS, contractGasProvider);
		}

		public BigInteger getGaslimit() 
		{
			return gaslimit;
		}

		public void setGaslimit(BigInteger gaslimit) 
		{
			this.gaslimit = gaslimit;
			contractGasProvider = new StaticGasProvider(gasprice, gaslimit);
		}

		public BigInteger getGasprice() 
		{
			return gasprice;
		}

		public void setGasprice(BigInteger gasprice) 
		{
			this.gasprice = gasprice;
			contractGasProvider = new StaticGasProvider(gasprice, gaslimit);
		}
		
		public static String extractField(String text, String fieldName) {
	        String pattern = fieldName + "='(.*?)'";
	        java.util.regex.Pattern regex = java.util.regex.Pattern.compile(pattern);
	        java.util.regex.Matcher matcher = regex.matcher(text);

	        if (matcher.find()) {
	            return matcher.group(1); // Extract the first captured group
	        } else {
	            return "Field not found";
	        }
	    }
}
