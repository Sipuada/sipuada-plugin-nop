package org.github.sipuada.plugins.nop;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.UserAgent;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.gov.nist.gnjvx.sdp.SessionDescriptionImpl;
import android.javax.sdp.SessionDescription;

public class NoOperationSipuadaPlugin implements SipuadaPlugin {
	
	private final Logger logger = LoggerFactory.getLogger(NoOperationSipuadaPlugin.class);
	
	public NoOperationSipuadaPlugin() {
		logger.info("{} sipuada plugin instantiated.", NoOperationSipuadaPlugin.class.getName());
	}

	@Override
	public SessionDescription generateOffer(String callId, RequestMethod method) {
		logger.info("{} generating offer in context of call invitation {} for a {} request...",
				NoOperationSipuadaPlugin.class.getName(), callId, method);
		return new SessionDescriptionImpl();
	}

	@Override
	public SessionDescription generateAnswer(String callId, RequestMethod method, SessionDescription offer) {
		logger.info("{} generating answer for offer {} in context of call invitation {} for a {} request...",
				NoOperationSipuadaPlugin.class.getName(), offer.toString(), callId, method);
		return null;
	}

	@Override
	public boolean performSessionSetup(String callId, UserAgent userAgent) {
		logger.info("{} performing session setup in context of call {}...",
				NoOperationSipuadaPlugin.class.getName(), callId);
		return false;
	}

	@Override
	public boolean performSessionTermination(String callId) {
		logger.info("{} performing session tear down in context of call {}...",
				NoOperationSipuadaPlugin.class.getName(), callId);
		return false;
	}

}
