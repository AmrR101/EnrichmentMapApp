package org.baderlab.csplugins.enrichmentmap.view.creation.string;

import java.util.Arrays;
import java.util.List;

import org.baderlab.csplugins.enrichmentmap.view.util.CardDialogPage;
import org.baderlab.csplugins.enrichmentmap.view.util.CardDialogParameters;

import com.google.inject.Inject;
import com.google.inject.Provider;

public class StringDialogParameters implements CardDialogParameters {

	@Inject private Provider<StringDialogPage> genemaniaDialogPage;
	
	@Override
	public String getTitle() {
		return "Create Enrichment Map from STRING Network";
	}

	@Override
	public List<CardDialogPage> getPages() {
		return Arrays.asList(genemaniaDialogPage.get());
	}

}