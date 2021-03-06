package org.baderlab.csplugins.enrichmentmap.commands.tunables;

import org.baderlab.csplugins.enrichmentmap.model.TableParameters;
import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyTableManager;
import org.cytoscape.work.Tunable;

import com.google.inject.Inject;

public class TableTunables {

	@Tunable(description="Specifies a table by table name. ", 
			longDescription = "If the prefix ```SUID:``` is used, the table corresponding the SUID will be returned. Example: \"galFiltered.sif default node\"")
	public String table;

	@Tunable(required=true, description="Name of column that contains the names of the gene sets.")
	public String nameColumn;
	
	@Tunable(required=true, description="Name of column that contains the list of genes.")
	public String genesColumn;
	
	@Tunable(required=true, description="Name of column that contains p values.")
	public String pvalueColumn;
	
	@Tunable(description="Name of column that contains the gene set description (optional).")
	public String descriptionColumn;

	
	@Inject private CyTableManager tableManager;
	
	

	private CyTable getTable() { 
		if(table == null)
			return null;

		if(table.toLowerCase().startsWith("suid:")) {
			String[] tokens = table.split(":");
			return tableManager.getTable(Long.parseLong(tokens[1].trim()));
		} else {
			for(CyTable t: tableManager.getAllTables(true)) {
				if(t.getTitle().equalsIgnoreCase(table)) {
					return t;
				}
			}
		}
		return null;
	}
	
	public TableParameters getTableParameters() throws IllegalArgumentException {
		CyTable table = getTable();
		if(table == null)
			throw new IllegalArgumentException("Table '" + table + "' is invalid.");
		
		validateColumn(table, nameColumn, false, String.class, true);
		validateColumn(table, genesColumn, true, String.class, true);
		validateColumn(table, pvalueColumn, false, Double.class, true);
		validateColumn(table, descriptionColumn, false, String.class, false);
		
		return new TableParameters(table, nameColumn, genesColumn, pvalueColumn, null, descriptionColumn, null);
	}
	
	
	private static void validateColumn(CyTable table, String name, boolean isList, Class<?> type, boolean required) throws IllegalArgumentException {
		if(name == null && required)
			throw new IllegalArgumentException("Column '" + name + "' is required.");
		if(name == null)
			return;
		
		CyColumn column = table.getColumn(name);
		if(column == null && required)
			throw new IllegalArgumentException("Column '" + name + "' is invalid.");
		if(column == null)
			return;
		
		// if the column exists it must still be of the required type
		if((isList && !column.getListElementType().equals(type)) || (!isList && !column.getType().equals(type)))
			throw new IllegalArgumentException("Column '" + name + "' must be of type '" + (isList ? "list of " + type : type) + "'");
	}
	
	
}

