package com.epimorphics.lda.renderers;

import java.io.*;
import java.util.*;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Element;
import org.w3c.dom.Document;

import com.epimorphics.jsonrdf.RDFUtil;
import com.epimorphics.lda.bindings.Bindings;
import com.epimorphics.lda.core.APIResultSet;
import com.epimorphics.lda.core.APIResultSet.MergedModels;
import com.epimorphics.lda.renderers.XMLRendering.Trail;
import com.epimorphics.lda.shortnames.CompleteContext.Mode;
import com.epimorphics.lda.shortnames.ShortnameService;
import com.epimorphics.lda.support.Times;
import com.epimorphics.lda.vocabularies.EXTRAS;
import com.epimorphics.lda.vocabularies.SKOSstub;
import com.epimorphics.util.*;
import com.epimorphics.vocabs.API;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.shared.PrefixMapping;
import com.hp.hpl.jena.shared.WrappedException;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDFS;

public class FeedRenderer implements Renderer {

	private final MediaType mt;
	private final Resource config;
	private final String namespace;
	private final ShortnameService sns;
	private final List<Property> dateProperties;
	private final List<Property> labelProperties;
	
	// TODO better than this
	private final String neverUpdated = "1954-02-04T00:00:01.52Z";
	
	public FeedRenderer
		( MediaType mt
		, Resource config
		, ShortnameService sns 
		) {
		this.mt = mt;
		this.config = config;
		this.sns = sns;
		this.dateProperties = getDateProperties( config );
		this.labelProperties = getLabelProperties( config );
		this.namespace = getConfiguredNamespace();
	}

	@Override public MediaType getMediaType(Bindings rc) {
		return mt;
	}

	@Override public Mode getMode() {
		return Mode.PreferPrefixes;
	}

	@Override public BytesOut render
		( final Times t
		, final Bindings b
		, final Map<String, String> termBindings
		, final APIResultSet results
		) {
		return new BytesOutTimed() {

			@Override protected void writeAll(OutputStream os) {
				renderFeed( os, results, t, termBindings, b );
				flush( os );
			}

			@Override protected String getFormat() {
				return FeedRendererFactory.format;
			}
			
		};
	}

	@Override public String getPreferredSuffix() {
		return FeedRendererFactory.format;
	}

	private static void flush( OutputStream os ) {
		try { os.flush(); } 
		catch (IOException e) { throw new WrappedException( e ); } 
	}

	/**
	    Return a list of date properties, most preferred first, from the
	    RDF list that is the value of the feedDateProperties value of
	    config. If there is no such value, then use the list
	    (dct:modified). 
	*/
	public List<Property> getDateProperties(Resource config) {
		List<Property> result = getPropertyList(config, EXTRAS.feedDateProperties);
		if (result.isEmpty()) {
			result.add( DCTerms.modified );
			result.add( DCTerms.date );
			result.add( DCTerms.dateAccepted );
			result.add( DCTerms.dateSubmitted );
			result.add( DCTerms.created );
		}
		return result;
	}

	private List<Property> getPropertyList(Resource config, Property property) {
		List<Property> result = new ArrayList<Property>();
		Statement s = config.getProperty( property );
		if (s != null) {
			for (RDFNode p: RDFUtil.asJavaList( s.getResource() )) {
				result.add( p.as( Property.class ) );
			}
		}
		return result;
	}
	
	/**
	    Return a list of label properties, most preferred first, from the
	    RDF list that is the value of the feedLabelProperties value of
	    config. If there is no such value, then use the list
	    (api:label, skos:prefLabel, rdfs:label).
	*/
	public List<Property> getLabelProperties( Resource config ) {
		List<Property> result = getPropertyList( config, EXTRAS.feedLabelProperties );
		if (result.isEmpty()) {
			result.add( API.label );
			result.add( SKOSstub.prefLabel );
			result.add( RDFS.label );
		}
		return result;
	}
	
	private void renderFeed( OutputStream os, APIResultSet results, Times t, Map<String, String> termBindings, Bindings b ) {
		final PrefixMapping pm = results.getModelPrefixes();
		Document d = DOMUtils.newDocument();
		FeedResults feedResults = new FeedResults
			( results.getRoot(), results.getResultList(), results.getModels() );
		renderFeedIntoDocument( d, termBindings, feedResults );
	//
		Transformer tr = DOMUtils.setPropertiesAndParams( t, b, pm, null );
		OutputStreamWriter u = StreamUtils.asUTF8( os );
		StreamResult sr = new StreamResult( u );
		try { tr.transform( new DOMSource( d ), sr ); } 
		catch (TransformerException e) { throw new WrappedException( e ); }
	}

	public static class FeedResults {
	
		final Resource root;
		final List<Resource> items;
		final MergedModels models;
		
		public FeedResults(Resource root, List<Resource> items, MergedModels models) {
			this.root = root;
			this.items = items;
			this.models = models;
		}

		public Resource getRoot() {
			return root;
		}

		public MergedModels getModels() {
			return models;
		}

		public List<Resource> getResultList() {
			return items;
		}
	}
	
	public void renderFeedIntoDocument
		( Document d
		, Map<String, String> termBindings
		, FeedResults results 
		) {
		Element feed = d.createElement( "feed" );
		feed.setAttribute( "xmlns", "http://www.w3.org/2005/Atom" );
	//
		addChild( feed, "title", getFeedTitle() );
		addLinkChild( feed, results.getRoot().getURI() );
		addChild( feed, "author", "<name>Nemo</name>" );
		addChild( feed, "id", results.getRoot().getURI() );
	//		
		MergedModels mm = results.getModels();
		XMLRendering xr = new XMLRendering
			( mm.getMergedModel()
			, sns.asContext()
			, termBindings
			, false
			, d 
			);
	//
		int size = results.getResultList().size();
		List<Couple<Resource, String>> items = new ArrayList<Couple<Resource, String>>(size);
		for (Resource r: results.getResultList()) 
			items.add( new Couple<Resource, String>( r, getFeedDate( r ) ) );
		Collections.sort( items, sortCouplesByString );
	//
		addChild( feed, "updated", items.get(0).b );		
	//
		for (Couple<Resource, String> item: items) {
			Resource r = item.a;
			Element entry = d.createElement( "entry" );
			addChild( entry, "title", getEntryTitle( r ) );
			addChild( entry, "updated", item.b );
			addChild( entry, "author", "<name>Nemo</name>" );
			addChild( entry, "id", r.getURI() );
			
			Element content = d.createElement( "content" );
			content.setAttribute( "type", "application/xml" );
			content.setAttribute( "xmlns", namespace );
			
			Set<Resource> cyclic = new HashSet<Resource>();
			Set<Resource> seen = new HashSet<Resource>();
			Set<Resource> blocked = new HashSet<Resource>();
			
			Trail t = new Trail( cyclic, seen, blocked );
			xr.expandProperties( t, content, r );

			entry.appendChild( content );

			feed.appendChild( entry );
		}
	//
		d.appendChild( feed );
	}
	
	private static final Comparator<Couple<Resource, String>> sortCouplesByString = new Comparator<Couple<Resource, String>>() {

		@Override public int compare( Couple<Resource, String> l, Couple<Resource, String> r) {
			return -l.b.compareTo( r.b );
		}
	};

	private String getFeedDate( Resource r ) {
		for (Property p: dateProperties) {
			Statement ps = r.getProperty( p );
			if (ps != null) return ps.getLiteral().getLexicalForm();
		}
		return neverUpdated;
	}

	private String getEntryTitle( Resource r ) {
		return getLabel( r );
	}
	
	private String getLabel( Resource r ) {
		for (Property lp: labelProperties ) {
			Statement labelStatement = r.getProperty( lp );
			if (labelStatement != null) return labelStatement.getString();
		}
		return r.getURI();
	}

	private void addLinkChild( Element feed, String root ) {
		Document d = feed.getOwnerDocument();
		Element child = d.createElement( "link" );
		child.setAttribute( "rel", "self" );
		child.setAttribute( "type", "application/atom+xml" );
		child.setAttribute( "href", root );
		feed.appendChild( child );
		
	}

	private String getFeedTitle() {
		return RDFUtils.getStringValue( config, EXTRAS.feedTitle, "Elda feed" );
	}

	private void addChild( Element e, String tag, String body ) {
		Document d = e.getOwnerDocument();
		Element child = d.createElement( tag );
		child.appendChild( d.createTextNode( body ) );
		e.appendChild( child );
	}
	
	protected String getConfiguredNamespace() {
		return RDFUtils.getStringValue( config,  EXTRAS.feedNamespace, EXTRAS.getURI() );
	}

	public String getNamespace() {
		return namespace;
	}

}