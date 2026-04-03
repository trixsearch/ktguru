import java.io.BufferedReader;
import java.io.InputStreamReader;



public class NetworkScanner {
	public static void main(String[] args) {
		
		String[] arr= {"Abhinav1.Sahu","Harshpal.Singh","Kamal6.Verma"};
		
		for(String nameString : arr) {
			StringBuilder aaneWala = new StringBuilder();
			try {
//				String nameString="Abhinav1.Sahu";
				ProcessBuilder pBuilder = new ProcessBuilder("net","user","/do",nameString);
				Process process =pBuilder.start();
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while((line=reader.readLine()) != null) {
					aaneWala.append(line).append("\n");
				}
				
				process.waitFor();
				// StringBuilder doesnot have contains method so we convert it and use contains method
				if(aaneWala.toString().contains("RESIGNED")){
				// or we can get the indexOf method , which returns index of the give word we write in it
//				if(aaneWala.indexOf("RESIGNED") != -1) {
					System.out.println(nameString+" : is resigned");
				}
//				System.out.println(aaneWala.toString());
			} catch (Exception e) {
				// TODO: handle exception
				System.out.println("got erro "+ e.getMessage());
			}
		}
	}
}
