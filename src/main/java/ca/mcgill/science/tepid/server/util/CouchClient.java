package ca.mcgill.science.tepid.server.util;

import shared.Config;
import shared.ConfigKeys;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

public class CouchClient
{

    private static WebTarget couchdb, barcodesdb, temdb;
    
    public synchronized static WebTarget getTepidWebTarget()
    {
    	if (couchdb == null)
    	{
    		HttpAuthenticationFeature basicAuthFeature = HttpAuthenticationFeature.basic(Config.getSetting(ConfigKeys.COUCHDB_USERNAME), Config.getSetting(ConfigKeys.COUCHDB_PASSWORD));
    		couchdb = ClientBuilder
    				.newBuilder()
    				.register(JacksonFeature.class)
    				.register(basicAuthFeature)
    				.build()
    				.target(Config.getSetting(ConfigKeys.COUCHDB_URL));
		System.err.println(Config.getSetting(ConfigKeys.COUCHDB_USERNAME) + " " + Config.getSetting(ConfigKeys.COUCHDB_PASSWORD));
    	}
    	return couchdb;
    }
    
    public synchronized static WebTarget getBarcodesWebTarget()
    {
    	if (barcodesdb == null)
    	{
    		HttpAuthenticationFeature basicAuthFeature = HttpAuthenticationFeature.basic(Config.getSetting(ConfigKeys.BARCODES_USERNAME), Config.getSetting(ConfigKeys.BARCODES_PASSWORD));
    		barcodesdb = ClientBuilder
    				.newBuilder()
    				.register(JacksonFeature.class)
    				.register(basicAuthFeature)
    				.build()
    				.target(Config.getSetting(ConfigKeys.BARCODES_URL));
    	}
    	return barcodesdb;
    }

    public synchronized static WebTarget getTemWebTarget()
    {
   	if (temdb == null)
    	{
   		temdb = ClientBuilder
				.newBuilder()
    				.register(JacksonFeature.class)
				.build()
				.target(Config.getSetting(ConfigKeys.TEM_URL));
    	}
    	return temdb;
    }

}
