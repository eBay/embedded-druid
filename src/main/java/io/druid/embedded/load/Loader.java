package io.druid.embedded.load;

import java.util.List;

import io.druid.data.input.InputRow;
import io.druid.data.input.impl.DimensionSchema;

/**
 * This abstract class is interface for loading data of various formats. Possible implementation can be
 * CSV, XML, JSON etc. Implementation class needs to provide iterator implementation. 
 *
 */
public abstract class Loader implements Iterable<InputRow> {
	  protected List<String> columns;
	  protected List<String> dimensions;
	  protected String timestampDimension;
	  
	  public Loader(List<String> cols, List<String> dims, String ts) {
		  this.columns = cols;
		  this.dimensions = dims;
		  this.timestampDimension = ts;
	  }
}
