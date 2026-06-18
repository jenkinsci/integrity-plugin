package hudson.scm;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

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
		public FormValidation doCheckClientId(@QueryParameter String value) {
			return requiredFieldValidation(value, "Client ID");
		}
		public FormValidation doCheckClientSecret(@QueryParameter String value) {
			return requiredFieldValidation(value, "Client Secret");
		}
		public FormValidation doCheckTokenEndpoint(@QueryParameter String value) {
			return requiredFieldValidation(value, "Token Endpoint");
		}
		public FormValidation doCheckOAuthScope(@QueryParameter String value) {
			return requiredFieldValidation(value, "Scope");
		}
		private FormValidation requiredFieldValidation(String value, String fieldName) {
			if (Util.fixEmptyAndTrim(value) == null) {
				return FormValidation.error(fieldName + " is required.");
			}
			return FormValidation.ok();
		}
		@Override
		public String getDisplayName() {
			return "OAuth2 Client Credentials";
		}
	}
	

}
