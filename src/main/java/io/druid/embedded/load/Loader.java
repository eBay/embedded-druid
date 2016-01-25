/*
 * Copyright 2015 eBay Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.embedded.load;

import java.util.List;

import io.druid.data.input.InputRow;

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
