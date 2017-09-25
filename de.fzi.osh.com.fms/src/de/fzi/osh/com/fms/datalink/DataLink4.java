package de.fzi.osh.com.fms.datalink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Container for converting DataLink type 4 data
 * 
 * @author K. Foerderer
 *
 */
public class DataLink4 {
	
	/**
	 * Representation of the header data for a datalink file. See documentation for more info
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static class Header {
		public final int format = 4;
		public String checkUnit = "";
		public String checkPlausibility = "";
		public String source = "";
		//String cultureInfo = "";
		public String timeZone = "";
	}
	
	/**
	 * Representation of a row in DataLink type 4 file format
	 * 
	 * @author K. Foerderer
	 *
	 */
	public static class Row {
		/**
		 * Column 1:
		 * ?
		 */
		public String id;
		/**
		 * Column 2:
		 * 
		 * YYYY-MM-DDThh:mi:sszzz
		 */
		public ZonedDateTime date;
		/**
		 * Column 3:
		 * 
		 * "15min"
		 */
		public String resolution = "15min";
		/**
		 * Column 4:
		 * 
		 * id of hypothesis (?)
		 */
		public String hypothesis = "";
		/**
		 * Column 5:
		 * 
		 * arbitrary quality (optional: Integer.MIN_VALUE = not used) 
		 */
		public int quality = Integer.MIN_VALUE;
		/**
		 * Column 6:
		 * 
		 * value, using decimal point
		 */
		public double value;
		/**
		 * Colum 7:
		 * 
		 * unit of value, e.g. Wh
		 */
		public String unit;
		/**
		 * Column 8+ optional
		 */		
		public String toCsv(String separator, NumberFormat numberFormat) {
			return 	id + separator + 
					date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + separator + 
					resolution + separator + 
					hypothesis + separator + 
					(quality == Integer.MIN_VALUE ? "" : quality) + separator + 
					numberFormat.format(value) + separator + 
					unit + System.lineSeparator();					
		}
		/**
		 * Parses a row from a string
		 * 
		 * @param line
		 * @param separator
		 * @throws ParseException 
		 */
		public void fromCsv(String line, String separator, NumberFormat numberFormat) throws ParseException {
			String[] values = line.split(separator);
			
			id = values[0];
			date = ZonedDateTime.parse(values[1]);
			resolution = values[2];
			hypothesis = values[3];
			quality = (values[4].length() == 0 ? Integer.MIN_VALUE : Integer.parseInt(values[4]));
			value = numberFormat.parse(values[5]).doubleValue();
			unit = values[6];
		}
	}
	
	private Header header;
	private List<Row> rows;
	
	/**
	 * Constructor
	 */
	public DataLink4() {
		rows = new ArrayList<Row>();
	}
	
	/**
	 * Sets the header
	 * 
	 * @param header
	 */
	public void setHeader(Header header) {
		this.header = header;
	}
	
	/**
	 * Returns the header
	 * 
	 * @return
	 */
	public Header getHeader() {
		return header;
	}
	
	/**
	 * Reads the container data from a file
	 * 
	 * @param file
	 * @throws Exception 
	 */
	public void read(String file, String separator, NumberFormat numberFormat) throws Exception {
		parse(new String(Files.readAllBytes(Paths.get(file))), separator, numberFormat);
	}
	
	/**
	 * Writes the container data to a file
	 * 
	 * @param file
	 * @throws IOException 
	 */
	public void write(String file, String separator, NumberFormat numberFormat) throws IOException {
		Files.write(Paths.get(file), compile(separator, numberFormat).getBytes());
	}
	
	/**
	 * Parses the content of a DataLink type 4 file
	 * 
	 * @param content
	 * @throws Exception 
	 */
	public void parse(String content, String separator, NumberFormat numberFormat) throws Exception {
		
		Scanner scanner = new Scanner(content);
		
		header = new Header();
		
		if(rows.size() > 0) {
			rows.clear();
		}
		
		Pattern metaPattern = Pattern.compile("'.*");
		Pattern headerPattern = Pattern.compile("'(.*)=([^;]*);*");
		
		try {
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
			
				Matcher metaMatcher = metaPattern.matcher(line);
				if(metaMatcher.matches()) {
					// header or comment
					
					Matcher headerMatcher = headerPattern.matcher(line);
					if(headerMatcher.matches()) {
						// header
						String parameter = headerMatcher.group(1);
						String value = headerMatcher.group(2);
						
						if(parameter.equalsIgnoreCase("format")) {
							if(Integer.parseInt(value) != 4) {
								throw new Exception("Wrong format version");
							}
						} else if(parameter.equalsIgnoreCase("CheckUnit")) {
							header.checkUnit = value;
						} else if(parameter.equalsIgnoreCase("CheckPlausibility")) {
							header.checkPlausibility = value;
						} else if(parameter.equalsIgnoreCase("Source")) {
							header.source = value;
						} else if(parameter.equalsIgnoreCase("TimeZone")) {
							header.timeZone = value;
						}
					}							
				} else {
					// read values
					Row row = new Row();
					row.fromCsv(line, separator, numberFormat);
					rows.add(row);
				}			
			}
		} catch(Exception e) {
			throw e;
		}
		finally {
			scanner.close();
		}	
	}
	
	/**
	 * Adds a row of data to the container
	 * 
	 * @param row
	 */
	public void addRow(Row row) {
		rows.add(row);
	}
	
	/**
	 * Returns the rows 
	 * 
	 * @return
	 */
	public List<Row> getRows() {
		return rows;
	}
	
	/**
	 * Compiles the data into the DataLink type 4 file format
	 * 
	 * @return
	 */
	public String compile(String separator, NumberFormat numberFormat) {
		// set up header
		String content =  "'Format=4" + System.lineSeparator();
		
		if(header.checkUnit.length() > 0) {
			content += "'CheckUnit=" + header.checkUnit + System.lineSeparator(); // implied by format=4
		}
		if(header.checkPlausibility.length() > 0) {
			content += "'CheckPlausibility=" + header.checkPlausibility + System.lineSeparator();	
		}
		if(header.source.length() > 0) {
			content += "'Source=" + header.source + System.lineSeparator();
		}
		if(header.timeZone.length() > 0) {
			content += "'TimeZone=" + header.timeZone + System.lineSeparator();
		}
		
		// compile rows
		for(Row row : rows) {
			content += row.toCsv(separator, numberFormat);
		}
		
		return content;
	}
}
