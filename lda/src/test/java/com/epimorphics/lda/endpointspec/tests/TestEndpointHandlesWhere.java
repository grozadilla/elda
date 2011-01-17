/*
    See lda-top/LICENCE (or http://elda.googlecode.com/hg/LICENCE)
    for the licence for this software.
    
    (c) Copyright 2011 Epimorphics Limited
    $Id$
*/

package com.epimorphics.lda.endpointspec.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import com.epimorphics.jsonrdf.utils.ModelIOUtils;
import com.epimorphics.lda.core.APIEndpointImpl;
import com.epimorphics.lda.core.APIEndpointSpec;
import com.epimorphics.lda.core.APISpec;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;

public class TestEndpointHandlesWhere 
	{
	Model spec = ModelIOUtils.modelFromTurtle
		( 
		":s a api:API; api:endpoint :e; api:sparqlEndpoint <http://example.com/none>."
		+ "\n:e a api:ListEndpoint; api:uriTemplate '/absent/friends'; api:where 'PONDENOME'." 
		);

	Resource s = spec.getResource( spec.expandPrefix( ":s" ) );
	Resource e = spec.getResource( spec.expandPrefix( ":e" ) );
	
	@Test public void testEndpointSpecExtractsWhere()
		{
		APISpec a = new APISpec( s, null );
		APIEndpointSpec eps = new APIEndpointSpec( a, null, e );
		assertEquals( "PONDENOME", eps.getWhere() );
		}
	
	@Test public void testEndpointImplUsedFixedSelect()
		{
		APISpec a = new APISpec( s, null );
		APIEndpointSpec eps = new APIEndpointSpec( a, null, e );
		APIEndpointImpl i = new APIEndpointImpl( eps );
		String q = i.getSelectQuery();
		if (!q.replaceAll( "[\n ]+", " " ).matches( "SELECT \\?item WHERE \\{ PONDENOME\\} OFFSET 0 LIMIT 10" ))
			{
			fail( "constructed query '" + q + "'\ndoes not contain api:where clause" );
			}
		}
	}
