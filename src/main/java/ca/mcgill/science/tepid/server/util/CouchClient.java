package ca.mcgill.science.tepid.server.util;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

public class CouchClient
{
	private static final String TEPID_DB_USERNAME_SETTING = "TEPID_DB_USERNAME", 
								TEPID_DB_PASSWORD_SETTING = "TEPID_DB_PASSWORD", 
								TEPID_DB_URL_SETTING = "TEPID_DB_URL",
								BARCODES_DB_USERNAME_SETTING = "BARCODES_DB_USERNAME",
								BARCODES_DB_PASSWORD_SETTING = "BARCODES_DB_PASSWORD",
								BARCODES_DB_URL_SETTING = "BARCODES_DB_URL",
								TEM_DB_URL_SETTING = "TEM_DB_URL";
	
	
	private static WebTarget couchdb, barcodesdb, temdb;
    
    public synchronized static WebTarget getTepidWebTarget()
    {
    	if (couchdb == null)
    	{
    		HttpAuthenticationFeature basicAuthFeature = HttpAuthenticationFeature.basic(Settings.getString(TEPID_DB_USERNAME_SETTING), Settings.getString(TEPID_DB_PASSWORD_SETTING));
    		couchdb = ClientBuilder
    				.newBuilder()
    				.register(JacksonFeature.class)
    				.register(basicAuthFeature)
    				.build()
    				.target(Settings.getString(TEPID_DB_URL_SETTING));
    	}
    	return couchdb;
    }
    
    public synchronized static WebTarget getBarcodesWebTarget()
    {
    	if (barcodesdb == null)
    	{
    		HttpAuthenticationFeature basicAuthFeature = HttpAuthenticationFeature.basic(Settings.getString(BARCODES_DB_USERNAME_SETTING), Settings.getString(BARCODES_DB_PASSWORD_SETTING));
    		barcodesdb = ClientBuilder
    				.newBuilder()
    				.register(JacksonFeature.class)
    				.register(basicAuthFeature)
    				.build()
    				.target(Settings.getString(BARCODES_DB_URL_SETTING));
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
   					.target(Settings.getString(TEM_DB_URL_SETTING));
    	}
    	return temdb;
    }
}
