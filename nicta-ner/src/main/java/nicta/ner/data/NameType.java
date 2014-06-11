/*
 * #%L
 * NICTA Named Entity Recogniser library
 * %%
 * Copyright (C) 2010 - 2014 NICTA
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package nicta.ner.data;

/**
 * This class is a name type class.
 * 
 * Each instance of this class represents a name type
 * such as:
 * ORGANIZATION, LOCATION, PERSON, DATE...
 * 
 * @author William Han
 *
 */
public class NameType {
	public String typeName = "";
	
	public static NameType NULL_TYPE = new NameType("UNKNOWN");
	public static NameType DATE_TYPE = new NameType("DATE");
	
	public NameType(String _name) {
		typeName = _name;
	}
	
	public String toString() {
		return typeName;
	}
}