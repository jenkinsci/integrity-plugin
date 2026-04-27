package hudson.scm;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class OAuth2ClientCredentials extends BaseStandardCredentials {
	private static final long serialVersionUID = 1L;

	private final String clientId;
	private final Secret clientSecret;
	private final String tokenEndpoint;
	private final String oAuthScope;

	@DataBoundConstructor
	public OAuth2ClientCredentials(String id,String description, String clientId, String clientSecret, String tokenEndpoint,  String oAuthScope) {
		super(id, description);
		this.clientId = clientId;
		this.clientSecret = Secret.fromString(clientSecret);
		this.tokenEndpoint = tokenEndpoint;
		 this.oAuthScope = oAuthScope;
	}

	public String getClientId() {
		return clientId;
	}

	public Secret getClientSecret() {
		return clientSecret;
	}
	
	public String getTokenEndpoint() {
		return tokenEndpoint;
	}
	
	public String getOAuthScope() {
		return oAuthScope;
	}
	
	@Extension
	public static class DescriptorImpl extends BaseStandardCredentials.BaseStandardCredentialsDescriptor {
		@Override
		public String getDisplayName() {
			return "OAuth2 Client Credentials";
		}
	}
	

}
