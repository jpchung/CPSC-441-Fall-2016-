import java.text.*;
import java.util.*;
import java.net.*;
import java.io.*;

/**
 * UrlCache Class
 *
 * @author Johnny Phuong Chung
 * @author 	Majid Ghaderi
 * @version	1.2, Oct 13 2016
 *
 */
public class UrlCache {

	//global variables
	HashMap<String, String> catalogMap;
	String fileName = "fileCatalog.txt";
	File file;

    /**
     * Default constructor to initialize data structures used for caching/etc
	 * If the cache already exists then load it. If any errors then throw exception.
	 *
     * @throws UrlCacheException if encounters any errors/exceptions
	 * HashMap implemented using the the following sources:
	 * <p/> https://docs.oracle.com/javase/7/docs/api/java/util/HashMap.html
	 * <p/> http://stackoverflow.com/questions/12747946/how-to-write-and-read-a-file-with-a-hashmap
	 * <p/> https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html
     */
	public UrlCache() throws UrlCacheException {

		//instantiate global variables
		catalogMap = new HashMap<String, String>();
		file =  new File(fileName);

		//if catalog file doesn't exist, create it first
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				throw new UrlCacheException();
			}
		}

		try {
			//load file and its properties
			Properties fileProp = new Properties();
			fileProp.load(new FileInputStream(fileName));

			//create HashMap entries for every item in catalog
			for (String key: fileProp.stringPropertyNames()) {
				catalogMap.put(key, fileProp.get(key).toString());
			}

		}
		catch(IOException e) {
			System.out.println("IO exception 1: " + e.getMessage());
			throw new UrlCacheException();
		}
	}
	
    /**
     * Downloads the object specified by the parameter url if the local copy is out of date.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     * @throws UrlCacheException if encounters any errors/exceptions
     */
	public void getObject(String url) throws UrlCacheException {

		String hostName, portName, pathName;
		int portNum;
		Socket socket;

		//split url into host and path components
		String[] urlComponents = url.split("/",2);

		//check for specified host port number
		if(urlComponents[0].contains(":")) {
			//split host name and specified port
			String[] hostAndPort = urlComponents[0].split(":",2);
			//initialize host, port
			hostName = hostAndPort[0];
			portName = hostAndPort[1];
		}
		// otherwise default port number is 80 (HTTP)
		else {
			//initialize host, port
			hostName = urlComponents[0];
			portName = "80";
		}
		pathName = "/" + urlComponents[1];
		portNum = Integer.parseInt(portName);

		//strings for HTTP request header
		String http_RequestLine_1 = "GET " + pathName + " HTTP/1.1\r\n";
		String http_RequestLine_2 = "Host: " + hostName + ":" + portName + "\r\n";
		String http_endHeader = "\r\n";

		//Establish TCP connection, prepare HTTP request
		try{
			//initialize socket to establish TCP connection
			socket = new Socket(hostName, portNum);

			//check if HashMap has previous entry for url
			if(catalogMap.containsKey(url)) {

				//add conditional GET to HTTP request header
				String http_RequestLine_3 = "If-Modified-Since: " + catalogMap.get(url) + "\r\n";
				String http_header = http_RequestLine_1 + http_RequestLine_2 + http_RequestLine_3+ http_endHeader;
				System.out.println(http_header);

				//write header to socket and flush to send HTTP request
				byte[] http_header_bytes = http_header.getBytes("US-ASCII");
				socket.getOutputStream().write(http_header_bytes);
				socket.getOutputStream().flush();

				//get HTTP response header
				String response_header = getResponseHeader(socket);
				System.out.println(response_header);

				//if already have most recent version of file, no need to get object
				if(response_header.contains("304 Not Modified"))
					return;

				//otherwise download new object and add entry to HashMap
				else if(response_header.contains("200 OK")) {
					//get byte length of object to download from response header
					String[] objectSizeTemp = response_header.split("Content-Length: ");
					String[] objectSize = objectSizeTemp[1].split("\r\n");
					int objLength = Integer.parseInt(objectSize[0]);

					//download the object
					getResponseObject(objLength, socket, pathName);

					//update last modified date of url object in HashMap
					String[] lastModifiedTempArray = response_header.split("Last-Modified: ");
					String[] lastModifiedArray = lastModifiedTempArray[1].split("\r\n");
					String lastModified = lastModifiedArray[0];
					catalogMap.put(url, lastModified);

				}

			}
			else {
				//Prepare HTTP request for new url
				String http_header = http_RequestLine_1 + http_RequestLine_2 + http_endHeader;
				System.out.println(http_header);

				//write header to socket and flush to send HTTP request
				byte[] http_header_bytes = http_header.getBytes("US-ASCII");
				socket.getOutputStream().write(http_header_bytes);
				socket.getOutputStream().flush();

				//get HTTP response header
				String response_header = getResponseHeader(socket);
				System.out.println(response_header);

				//get byte length of object to download from response header
				String[] objectSizeTemp = response_header.split("Content-Length: ");
				String[] objectSize = objectSizeTemp[1].split("\r\n");
				int objLength = Integer.parseInt(objectSize[0]);


				//download the object
				getResponseObject(objLength, socket, pathName);

				//update last modified date of url object in HashMap
				String[] lastModifiedTempArray = response_header.split("Last-Modified: ");
				String[] lastModifiedArray = lastModifiedTempArray[1].split("\r\n");
				String lastModified = lastModifiedArray[0];
				catalogMap.put(url, lastModified);

			}

			//save HashMap entries and store into catalog file
			Properties fileProp  = new Properties();
			for(Map.Entry<String, String> entry: catalogMap.entrySet()) {
				fileProp.put(entry.getKey(), entry.getValue());
			}
			fileProp.store(new FileOutputStream(fileName), null);

		}
		catch(IOException e) {
			System.out.println("IO exception 2: " + e.getMessage());
			throw new UrlCacheException();
		}

	}


    /**
     * Returns the Last-Modified time associated with the object specified by the parameter url.
	 *
     * @param url 	URL of the object 
	 * @return the Last-Modified time in millisecond as in Date.getTime()
     * @throws UrlCacheException if the specified url is not in the cache, or there are other errors/exceptions
     */
	public long getLastModified(String url) throws UrlCacheException {
		//declare date variables
		DateFormat urlCatalogDate = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz");
		Date dateModified;

		//if url not in HashMap, throw exception since can't get last modified date
		if (!catalogMap.containsKey(url)) {
			System.out.println("Exception: URL not in catalog, cannot get Last Modified Date");
			throw new UrlCacheException();
		}

		//get url from HashMap
		String urlCatalogEntry = catalogMap.get(url);

		//parse through url HashMap entry for last modified date
		try {
			dateModified = urlCatalogDate.parse(urlCatalogEntry);
		}
		//if can't parse, catch exception
		catch(ParseException e){
			System.out.println("Parse exception: " + e.getMessage());
			throw new UrlCacheException();
		}
		return dateModified.getTime();

	}

	/**
	 * Parse through TCP response for header content
	 *
	 * @param socket Socket for TCP connection
	 * @return HTTP response header as a String
	 * @throws IOException
     */
	public String getResponseHeader(Socket socket) throws IOException {
		//assume response header <= 2KB
		byte[] response_header_bytes = new byte[2048];
		String response_header_string = "";
		int offset = 0;

		//parse response bytes sent through socket until end of header
		while(!response_header_string.contains("\r\n\r\n")) {
			socket.getInputStream().read(response_header_bytes, offset, 1);

			//append characters per iteration to build response header
			char currentChar = (char) response_header_bytes[offset++];
			response_header_string += currentChar;
		}

		return response_header_string;

	}

	/**
	 * Reads the response bytes from the TCP socket to generate byte array for file download
	 *
	 * @param objLength byte length of object
	 * @param socket socket for TCP connection
	 * @param pathName path name of object to get from HTTP request/response
	 * @throws IOException
     */
	public void getResponseObject(int objLength, Socket socket, String pathName) throws IOException {
		//each packet 1500 bytes, experiment with byte array for DL speed change
		byte[] response_object_bytes = new byte[objLength+1];
		int counter = 0;

		//read bytes for response object
		while(true) {
			//if read whole object length, break loop
			if (counter == objLength)
				break;

			//store read bytes into byte array
			socket.getInputStream().read(response_object_bytes, counter, 1);
			counter++;
		}

		//convert pathName to string array for file
		String[] fileNameArray = pathName.split("/");
		//pass object byte array and file type from path name, to write/create file
		writeObjectToFile(fileNameArray[fileNameArray.length - 1], response_object_bytes);

	}

	/**
	 * Takes byte array of object and writes to file
	 *
	 * @param fileName name of file
	 * @param objectBytes byte array to write as file
	 * @throws IOException
     */
	public void writeObjectToFile(String fileName, byte[] objectBytes) throws IOException {
		//initialize output stream for file
		FileOutputStream fileStream = new FileOutputStream(fileName);

		//write byte array to stream then close stream
		fileStream.write(objectBytes);
		fileStream.close();
	}

}
