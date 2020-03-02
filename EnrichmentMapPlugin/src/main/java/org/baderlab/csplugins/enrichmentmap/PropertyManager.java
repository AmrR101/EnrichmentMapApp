package org.baderlab.csplugins.enrichmentmap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.baderlab.csplugins.enrichmentmap.actions.OpenPathwayCommonsTask;
import org.baderlab.csplugins.enrichmentmap.view.creation.genemania.GenemaniaDialogParameters;
import org.baderlab.csplugins.enrichmentmap.view.creation.genemania.StringDialogParameters;
import org.baderlab.csplugins.enrichmentmap.view.heatmap.HeatMapParams.Distance;
import org.cytoscape.property.CyProperty;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Manages the CyProperties for EnrichmentMap.
 */
@Singleton
public class PropertyManager {
	
	@FunctionalInterface
	public interface PropertyListener<T> {
		void propertyChanged(Property<T> prop, T value);
	}
	
	private final Map<Property<?>,List<PropertyListener<?>>> listeners = new HashMap<>();
	
	
	public static final Property<Boolean>  HEATMAP_AUTOFOCUS    = Property.of("heatmapAutofocus", false);
	public static final Property<Boolean>  HEATMAP_DATASET_SYNC = Property.of("heatmapDatasetSync", true);
	public static final Property<Boolean>  HEATMAP_AUTO_SORT    = Property.of("heatmapAutoSort", true);
	public static final Property<Boolean>  HEATMAP_SELECT_SYNC  = Property.of("heatmapSelectSync", true);
	public static final Property<Double>   P_VALUE              = Property.of("default.pvalue", 1.0);
	public static final Property<Double>   Q_VALUE              = Property.of("default.qvalue", 0.1);
	public static final Property<Boolean>  CREATE_WARN          = Property.of("create.warn", true);
	public static final Property<Distance> DISTANCE_METRIC      = Property.of("default.distanceMetric", Distance.PEARSON, Distance::valueOf);
	public static final Property<String>   PATHWAY_COMMONS_URL  = Property.of("pathway.commons.url", OpenPathwayCommonsTask.DEFAULT_BASE_URL);
	
	public static final Property<String> STRING_COLUMN_NAME  = Property.of("string.column.name",  StringDialogParameters.NAME_COLUMN_DEF);
	public static final Property<String> STRING_COLUMN_FDR   = Property.of("string.column.fdr",   StringDialogParameters.FDR_COLUMN_DEF);
	public static final Property<String> STRING_COLUMN_GENES = Property.of("string.column.genes", StringDialogParameters.GENES_COLUMN_DEF);
	public static final Property<String> STRING_COLUMN_DESC  = Property.of("string.column.descr", StringDialogParameters.DESC_COLUMN_DEF);
	public static final Property<String> STRING_COLUMN_SUID  = Property.of("string.column.suid",  StringDialogParameters.SUID_COLUMN_DEF);
	
	public static final Property<String> GENEMANIA_COLUMN_ANNOTATIONS  = Property.of("genemania.column.annotations", GenemaniaDialogParameters.ANNOTATIONS_COLUMN_DEF);
	public static final Property<String> GENEMANIA_COLUMN_GENE_NAME    = Property.of("genemania.column.genename",    GenemaniaDialogParameters.GENE_NAME_COLUMN_DEF);
	public static final Property<String> GENEMANIA_COLUMN_ORGANISM     = Property.of("genemania.column.organism",    GenemaniaDialogParameters.ORGANISM_COLUMN_DEF);
	public static final Property<String> GENEMANIA_COLUMN_ANN_NAME     = Property.of("genemania.column.annname",     GenemaniaDialogParameters.ANNOTATION_NAME_COLUMN_DEF);
	
	
	
	@Inject private CyProperty<Properties> cyProps;
	
	@AfterInjection
	private void initializeProperties() {
		getAllProperties()
			.stream()
			.filter(prop -> !cyProps.getProperties().containsKey(prop.key))
			.forEach(this::setDefault);
	}
	
	public <T> void addListener(Property<T> property, PropertyListener<T> listener) {
		listeners.computeIfAbsent(property, k -> new ArrayList<>()).add(listener);
	}
	
	public <T> void setValue(Property<T> property, T value) {
		cyProps.getProperties().setProperty(property.key, String.valueOf(value));
		
		for(PropertyListener<?> listener : listeners.getOrDefault(property, Collections.emptyList())) {
			((PropertyListener<T>)listener).propertyChanged(property, value);
		}
	}
	
	public <T> void setDefault(Property<T> property) {
		setValue(property, property.def);
	}
	
	public <T> T getValue(Property<T> property) {
		if(cyProps == null) // happens in JUnits
			return property.def;
		Properties properties = cyProps.getProperties();
		if(properties == null)
			return property.def;
		String string = properties.getProperty(property.key);
		if(string == null)
			return property.def;
		
		try {
			return property.converter.apply(string);
		} catch(Exception e) {
			return property.def;
		}
	}
	
	public boolean isTrue(Property<Boolean> property) {
		return Boolean.TRUE.equals(getValue(property));
	}
	
	@SuppressWarnings("rawtypes")
	public static List<Property<?>> getAllProperties() {
		List<Property<?>> properties = new ArrayList<>();
		for (Field field : PropertyManager.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(Property.class)) {
				try {
					properties.add((Property) field.get(null));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
		return properties;
	}
	
	
	public static class Property<T> {
		private final String key;
		public final T def;
		private final Function<String,T> converter;
		
		private Property(String key, T defaultValue, Function<String,T> converter) {
			this.key = key;
			this.def = defaultValue;
			this.converter = converter;
		}
		
		public static <T> Property<T> of(String key, T defaultValue, Function<String,T> converter) {
			return new Property<>(key, defaultValue, converter);
		}
		public static Property<Boolean> of(String key, boolean defaultValue) {
			return new Property<>(key, defaultValue, Boolean::valueOf);
		}
		public static Property<String> of(String key, String defaultValue) {
			return new Property<>(key, defaultValue, String::valueOf);
		}
		public static Property<Double> of(String key, double defaultValue) {
			return new Property<>(key, defaultValue, Double::valueOf);
		}
	}
}
