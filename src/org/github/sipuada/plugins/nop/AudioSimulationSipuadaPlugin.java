package org.github.sipuada.plugins.nop;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipUserAgent;
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
import android.javax.sdp.MediaDescription;
import android.javax.sdp.SdpConstants;
import android.javax.sdp.SdpException;
import android.javax.sdp.SdpFactory;
import android.javax.sdp.SessionDescription;

public class AudioSimulationSipuadaPlugin implements SipuadaPlugin {

	private final Logger logger = LoggerFactory.getLogger
		(AudioSimulationSipuadaPlugin.class);

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

    public enum CallRole {
        CALLEE,
        CALLER
    }
    private final Map<String, CallRole> roles = new HashMap<>();

    public enum SupportedAudioCodec {
    	PCMA_8("PCMA/8000", 8, 8000),
    	SPEEX_8("SPEEX/8000", 97, 8000),
    	SPEEX_16("SPEEX/16000", 97, 16000),
    	SPEEX_32("SPEEX/32000", 97, 32000);

    	private final String rtpmap;
    	private final int type;
    	private final int frequency;

    	private SupportedAudioCodec(String rtpmap, int type, int frequency) {
    		this.rtpmap = rtpmap;
    		this.type = type;
    		this.frequency = frequency;
    	}

		public String getRtpmap() {
			return rtpmap;
		}

		public int getType() {
			return type;
		}

		public int getFrequency() {
			return frequency;
		}

    }

    private final String identifier;

	public AudioSimulationSipuadaPlugin(String identifier) {
		this.identifier = identifier;
		logger.info("{} sipuada plugin for {} instantiated.",
			AudioSimulationSipuadaPlugin.class.getName(), identifier);
	}

	@Override
	public SessionDescription generateOffer(String callId, RequestMethod method,
			String localAddress) {
		roles.put(callId,  CallRole.CALLER);
		try {
			SessionDescription offer = createSdpOffer(localAddress);
			records.put(callId, new Record(offer));
			logger.info("{} generating offer {{}} in context of call invitation {} "
				+ "for a {} request...", AudioSimulationSipuadaPlugin.class.getName(),
				offer, callId, method);
			return includeOfferedMediaTypes(offer);
		} catch (Throwable anyIssue) {
			logger.error("{} could not generate offer in context of call invitation {} "
				+ "for a {} request...", AudioSimulationSipuadaPlugin.class.getName(),
				callId, method, anyIssue);
			return null;
		}
	}

	@Override
	public void receiveAnswerToAcceptedOffer(String callId, SessionDescription answer) {
		Record record = records.get(callId);
		record.setAnswer(answer);
		logger.info("{} received answer {{}} to offer {{}} in context of call "
			+ "invitation {}...", AudioSimulationSipuadaPlugin.class.getName(),
			answer, record.getOffer(), callId);
	}

	@Override
	public SessionDescription generateAnswer(String callId, RequestMethod method,
			SessionDescription offer, String localAddress) {
        roles.put(callId, CallRole.CALLEE);
        try {
    		SessionDescription answer = createSdpAnswer(offer, localAddress);
    		records.put(callId, new Record(offer, answer));
    		logger.info("{} generating answer {{}} to offer {{}} in context "
    			+ "of call invitation {} for a {} request...",
    			AudioSimulationSipuadaPlugin.class.getName(),
    			answer, offer, callId, method);
    		return includeAcceptedMediaTypes(answer, offer);
        } catch (Throwable anyIssue) {
			logger.error("{} could not generate answer to offer {{}} in context of "
				+ "call invitation {} for a {} request...",
				AudioSimulationSipuadaPlugin.class.getName(),
				offer, callId, method, anyIssue);
			return null;
        }
	}

	private SessionDescription createSdpOffer(String localAddress)
			throws SdpException {
		return createSdp(localAddress, System.currentTimeMillis() / 1000, 0L, "-");
	}

	private SessionDescription createSdpAnswer(SessionDescription offer,
			String localAddress) throws SdpException {
		return createSdp(localAddress, offer.getOrigin().getSessionId(),
			offer.getOrigin().getSessionVersion(), offer.getSessionName().getValue());
	}

	private SessionDescription createSdp(String localAddress, long sessionId,
			long sessionVersion, String sessionName) throws SdpException {
		SessionDescription sdp = SdpFactory.getInstance()
			.createSessionDescription(localAddress);
		OriginField originField = createOriginField(sdp, sessionId, sessionVersion);
		sdp.setOrigin(originField);
		ConnectionField connectionField = createConnectionField(localAddress);
		sdp.setConnection(connectionField);
		SessionNameField sessionNameField = createSessionNameField(sessionName);
		sdp.setSessionName(sessionNameField);
		return sdp;
	}

	private OriginField createOriginField(SessionDescription sdp,
			long sessionId, long sessionVersion) throws SdpException {
		OriginField originField = new OriginField();
		originField.setSessionId(sessionId);
		originField.setUsername(identifier);
		originField.setSessionVersion(sessionVersion);
		originField.setNetworkType(SDPKeywords.IN);
		originField.setAddressType(SDPKeywords.IPV4);
		return originField;
	}

	private ConnectionField createConnectionField(String localAddress)
			throws SdpException {
		ConnectionField connectionField = new ConnectionField();
		connectionField.setNetworkType(SDPKeywords.IN);
		connectionField.setAddressType(SDPKeywords.IPV4);
		connectionField.setAddress(localAddress);
		return connectionField;
	}

	private SessionNameField createSessionNameField(String sessionName)
			throws SdpException {
		SessionNameField sessionNameField = new SessionNameField();
		sessionNameField.setSessionName(sessionName);
		return sessionNameField;
	}

	private SessionDescription includeOfferedMediaTypes(SessionDescription offer)
			throws SdpException {
		MediaField mediaField = new MediaField();
		Vector<String> mediaFormats = new Vector<>();
		Vector<MediaDescription> mediaDescriptions = new Vector<>();
		for (SupportedAudioCodec audioCodec : SupportedAudioCodec.values()) {
			MediaDescriptionImpl mediaDescription = new MediaDescriptionImpl();
			mediaFormats.add(Integer.toString(audioCodec.type));
			mediaDescription.setMediaField(mediaField);
			AttributeField attributeField = new AttributeField();
			attributeField.setName(SdpConstants.RTPMAP);
			attributeField.setValue(String.format(Locale.US, "%d %s",
				audioCodec.type, audioCodec.rtpmap));
			mediaDescription.addAttribute(attributeField);
			mediaDescriptions.add(mediaDescription);
		}
		mediaField.setMediaFormats(mediaFormats);
		mediaField.setMedia("audio");
		mediaField.setMediaType("audio");
		mediaField.setProtocol(SdpConstants.RTP_AVP);
		int localAudioPort = new Random().nextInt((32767 - 16384)) + 16384;
		mediaField.setPort(localAudioPort);
		MediaDescription finalMediaDescription = new MediaDescriptionImpl();
		AttributeField rtcpAttribute = new AttributeField();
		rtcpAttribute.setName("rtcp");
		rtcpAttribute.setValue(Integer.toString(localAudioPort));
		finalMediaDescription.addAttribute(rtcpAttribute);
		AttributeField sendReceiveAttribute = new AttributeField();
		sendReceiveAttribute.setValue("sendrecv");
		finalMediaDescription.addAttribute(sendReceiveAttribute);
		mediaDescriptions.add(finalMediaDescription);
		offer.setMediaDescriptions(mediaDescriptions);
		return offer;
	}

	@SuppressWarnings("unchecked")
	private SessionDescription includeAcceptedMediaTypes(SessionDescription answer,
			SessionDescription offer) throws SdpException {
		Vector<MediaDescription> offerMediaDescriptions = offer
			.getMediaDescriptions(false);
		if (offerMediaDescriptions == null || offerMediaDescriptions.isEmpty()) {
			return null;
		}
		MediaField mediaField = new MediaField();
		Vector<String> mediaFormats = new Vector<>();
		Vector<MediaDescription> answerMediaDescriptions = new Vector<>();
		for (SupportedAudioCodec audioCodec : SupportedAudioCodec.values()) {
			for (MediaDescription mediaDescription : offerMediaDescriptions) {
				Vector<AttributeField> attributeFields
					= ((MediaDescription) mediaDescription).getAttributes(false);
				for (AttributeField attributeField : attributeFields) {
					if (attributeField.getName().equals(SdpConstants.RTPMAP)) {
						int type = Integer.parseInt(attributeField.getValue()
							.split(" ")[0].trim());
						String rtpmap = attributeField.getValue()
							.split(" ")[1].trim();
						if ((type >= 0 && type <= 34 && type == audioCodec.type)
								|| rtpmap.toUpperCase().equals(audioCodec.rtpmap)) {
							((MediaDescriptionImpl) mediaDescription)
								.setMediaField(mediaField);
							mediaFormats.add(Integer.toString(type));
							answerMediaDescriptions.add(mediaDescription);
						}
					}
				}
			}
		}
		mediaField.setMediaFormats(mediaFormats);
		mediaField.setMedia("audio");
		mediaField.setMediaType("audio");
		mediaField.setProtocol(SdpConstants.RTP_AVP);
		int localAudioPort = new Random().nextInt((32767 - 16384)) + 16384;
		mediaField.setPort(localAudioPort);
		MediaDescription finalMediaDescription = new MediaDescriptionImpl();
		AttributeField rtcpAttribute = new AttributeField();
		rtcpAttribute.setName("rtcp");
		rtcpAttribute.setValue(Integer.toString(localAudioPort));
		finalMediaDescription.addAttribute(rtcpAttribute);
		AttributeField sendReceiveAttribute = new AttributeField();
		sendReceiveAttribute.setValue("sendrecv");
		finalMediaDescription.addAttribute(sendReceiveAttribute);
		answerMediaDescriptions.add(finalMediaDescription);
		answer.setMediaDescriptions(answerMediaDescriptions);
		return answer;
	}

	@Override
	public boolean performSessionSetup(String callId, SipUserAgent userAgent) {
		Record record = records.get(callId);
		SessionDescription offer = record.getOffer(), answer = record.getAnswer();
		switch (roles.get(callId)) {
			case CALLEE:
				logger.info("{} performing session setup in context of call {}...\n"
					+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}}",
					AudioSimulationSipuadaPlugin.class.getName(),
					callId, CallRole.CALLEE, offer, answer);
				break;
			case CALLER:
				logger.info("{} performing session setup in context of call {}...\n"
					+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}}",
					AudioSimulationSipuadaPlugin.class.getName(),
					callId, CallRole.CALLER, offer, answer);				
				break;
		}
		return true;
	}

	@Override
	public boolean performSessionTermination(String callId) {
		records.remove(callId);
		logger.info("{} performing session tear down in context of call {}...",
			AudioSimulationSipuadaPlugin.class.getName(), callId);
		return true;
	}

}
