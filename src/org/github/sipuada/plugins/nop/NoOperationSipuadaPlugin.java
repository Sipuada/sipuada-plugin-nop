package org.github.sipuada.plugins.nop;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.UserAgent;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.gov.nist.gnjvx.sdp.MediaDescriptionImpl;
import android.gov.nist.gnjvx.sdp.fields.AttributeField;
import android.gov.nist.gnjvx.sdp.fields.ConnectionField;
import android.gov.nist.gnjvx.sdp.fields.MediaField;
import android.gov.nist.gnjvx.sdp.fields.OriginField;
import android.gov.nist.gnjvx.sdp.fields.SDPKeywords;
import android.gov.nist.gnjvx.sdp.fields.SessionNameField;
import android.javax.sdp.SdpConstants;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SessionDescription;

public class NoOperationSipuadaPlugin implements SipuadaPlugin {
	
	private final Logger logger = LoggerFactory.getLogger(NoOperationSipuadaPlugin.class);

	class Record {
		Map<String, SessionDescription> storage = new HashMap<>();
		public Record(SessionDescription offer) {
			storage.put("offer", offer);
		}
		public Record(SessionDescription offer, SessionDescription answer) {
			storage.put("offer", offer);
			storage.put("answer", answer);
		}
		public SessionDescription getOffer() {
			return storage.get("offer");
		}
		public void setOffer(SessionDescription offer) {
			storage.put("offer", offer);
		}
		public SessionDescription getAnswer() {
			return storage.get("answer");
		}
		public void setAnswer(SessionDescription answer) {
			storage.put("answer", answer);
		}
	}
	private final Map<String, Record> records = new HashMap<>();

	public NoOperationSipuadaPlugin() {
		logger.info("{} sipuada plugin instantiated.", NoOperationSipuadaPlugin.class.getName());
	}

	@Override
	public SessionDescription generateOffer(String callId, RequestMethod method) {
		SessionDescription offer = createSdp();
		logger.info("{} generating offer {{}} in context of call invitation {} for a {} request...",
				NoOperationSipuadaPlugin.class.getName(), offer, callId, method);
		records.put(callId, new Record(offer));
		return offer;
	}

	@Override
	public void receiveAnswerToAcceptedOffer(String callId, SessionDescription answer) {
		Record record = records.get(callId);
		record.setAnswer(answer);
		logger.info("{} received answer {{}} to offer {{}} in context of call invitation {}...",
				NoOperationSipuadaPlugin.class.getName(), answer, record.getOffer(), callId);
	}

	@Override
	public SessionDescription generateAnswer(String callId, RequestMethod method, SessionDescription offer) {
		SessionDescription answer = createSdp();
		records.put(callId, new Record(offer, answer));
		logger.info("{} generating answer {{}} to offer {{}} in context of call invitation {} for a {} request...",
				NoOperationSipuadaPlugin.class.getName(), answer, offer, callId, method);
		return answer;
	}

	@Override
	public boolean performSessionSetup(String callId, UserAgent userAgent) {
		Record record = records.get(callId);
		SessionDescription offer = record.getOffer(), answer = record.getAnswer();
		logger.info("{} performing session setup in context of call {}...\nOffer: {{}}\nAnswer: {{}}",
				NoOperationSipuadaPlugin.class.getName(), callId, offer, answer);
		return false;
	}

	@Override
	public boolean performSessionTermination(String callId) {
		logger.info("{} performing session tear down in context of call {}...",
				NoOperationSipuadaPlugin.class.getName(), callId);
		records.remove(callId);
		return false;
	}

	private SessionDescription createSdp() {
		SessionDescription sdp;
		try {
			sdp = SdpFactory.getInstance().createSessionDescription();
		} catch (SdpException e) {
			e.printStackTrace();
			return null;
		}
		try {
			String sessionName = "-", localIpAddress = "192.168.130.207";
			long sessionId = (long) Math.random() * 100000000L;
			long sessionVersion = sessionId;
			OriginField originField = new OriginField();
			originField.setUsername("NoOpSipuadaPlug-in");
			originField.setSessionId(sessionId);
			originField.setSessVersion(sessionVersion);
			originField.setNetworkType(SDPKeywords.IN);
			originField.setAddressType(SDPKeywords.IPV4);
			originField.setAddress(localIpAddress);
	
			SessionNameField sessionNameField = new SessionNameField();
			sessionNameField.setSessionName(sessionName);
	
			ConnectionField connectionField = new ConnectionField();
			connectionField.setNetworkType(SDPKeywords.IN);
			connectionField.setAddressType(SDPKeywords.IPV4);
			connectionField.setAddress(localIpAddress);
	
			Vector<Object> mediaDescriptions = new Vector<>();
	
			MediaField audioField = new MediaField();
			audioField.setMediaType("audio");
			audioField.setPort(8080);
			audioField.setProtocol(SdpConstants.RTP_AVP);
	
			Vector<String> audioFormats = new Vector<String>();
			audioFormats.add(Integer.toString(SdpConstants.PCMA));
			audioField.setMediaFormats(audioFormats);
	
			MediaDescriptionImpl audioDescription = new MediaDescriptionImpl();
	
			AttributeField attributeField = new AttributeField();
			attributeField.setName(SdpConstants.RTPMAP);
			attributeField.setValue(String.format("%d %s/%d", SdpConstants.PCMA, "PCMA", 8000));
			audioDescription.addAttribute(attributeField);
	
			AttributeField sendReceive = new AttributeField();
			sendReceive.setValue("sendrecv");
			audioDescription.addAttribute(sendReceive);
	
			AttributeField rtcpAttribute = new AttributeField();
			rtcpAttribute.setName("rtcp");
			rtcpAttribute.setValue("38219");
			audioDescription.addAttribute(rtcpAttribute);
	
			mediaDescriptions.add(audioField);
			mediaDescriptions.add(audioDescription);
	
			sdp.setOrigin(originField);
			sdp.setSessionName(sessionNameField);
			sdp.setConnection(connectionField);
			sdp.setMediaDescriptions(mediaDescriptions);
		} catch (SdpException sdpException) {
			sdpException.printStackTrace();
		}
		return sdp;
	}

}
