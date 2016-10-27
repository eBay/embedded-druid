package io.druid.embedded.load.impl;

import java.util.Iterator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;

import io.druid.data.input.InputRow;
import io.druid.data.input.MapBasedInputRow;
import io.druid.data.input.impl.DimensionSchema;
import io.druid.embedded.load.Loader;

/**
 * This is CSV loader implementation where data are comma separated.
 * If column name is "value", then it is assuming containing metric value and will be converted as float.
 * For specifying any metric name, metric column should be present in each record.
 * Ex : For say wikipedia schema, having dimensions
 * "Timestamp, Page, Username, Gender, City"
 * and for Metric "CharsAdded" with value, CSV file can look like
 * 1234,JB,Abc,Male,SF,CharsAdded,1290
 * 1235,JB,Xyz,Female,SJ,CharsAdded,3421
 * where data in format "Timestamp, Page, Username, Gender, City, metric, value"
 */
public class CSVLoader extends Loader {

	protected Reader reader;
	
	/**
	 * @param reader : Reader object pointing to CSV file
	 * @param columns : List of all columns in CSV file (including metric and "value" column)
	 * @param columns2 : List of dimensions (Excluding metric and value columns)
	 * @param timestampDimension : Dimension which indicates timestamp field in CSV File.
	 */
	public CSVLoader(Reader reader, List<String> columns, List<String> dims, String timestampDimension) {
		super(columns, dims, timestampDimension);
		this.reader = reader;
	}
	
	@Override
	public Iterator<InputRow> iterator() {
		return new CSVReaderIterator();
	}

	protected Map<String, Object> parse(String row) {
		List<String> data = new ArrayList<String>();
		StringTokenizer stk = new StringTokenizer(row, ",");
		while(stk.hasMoreTokens()) {
			data.add(stk.nextToken());
		}
	    Map<String, Object> map = new HashMap<String, Object>();
	    if(data.size() != columns.size()) {
	    	return null;
	    }
	    for (int i = 0; i < columns.size(); i++) {
	    	if (data.get(i).equals("null") || data.get(i).isEmpty()) {
	    		continue;
	    	} else {
	    		//Column name "value" is treated as special column containing value of metric
	    		if (columns.get(i).equals("value")) {
	    			map.put(columns.get(i), Float.parseFloat(data.get(i)));
	    		} else {
	    			map.put(columns.get(i), data.get(i));
	    		}
	    	}
	    }
	    return map;
	}
	
	private class CSVReaderIterator implements Iterator<InputRow> {
		String nextLine;
		protected BufferedReader breader;
	    	
	    public CSVReaderIterator() {
	      this.breader = new BufferedReader(reader);
	    }

	    protected Long getTimestamp(Map<String, Object> map) {
	    	if (timestampDimension == null) {
	    		return 1l;
	    	} else {
	    		return (Long) Long.valueOf((String)map.get(timestampDimension));
	    	}
	    }

	    public boolean hasNext() {
	    	try {
	    		if (nextLine == null && (nextLine = breader.readLine()) == null) {
	    			close();
	    			return false;
	    		} else {
	    			return true;
	    		}
	    	} catch (IOException e) {
	    		e.printStackTrace();
	    		close();
	    		return false;
	    	}
	    }

	    public InputRow next() {
	    	if (nextLine == null) {
	    		try {
	    			nextLine = breader.readLine();
	    		} catch (IOException e) {
	    			e.printStackTrace();
	    			close();
	    			return null;
	    		}
	    	}
	    	Map<String, Object> map = parse(nextLine);
	    	if(map == null) {
		    	nextLine = null;
	    		next();
	    	}
	    	InputRow row = new MapBasedInputRow(getTimestamp(map), dimensions, map);
	    	nextLine = null;
	    	return row;
	    }

	    public void remove() {
	      throw new UnsupportedOperationException();
	    }
	    
	    public void close() {
	    	try {
	    		breader.close();
	    	} catch(Exception e) {
	    	}
	    }
	}
}
