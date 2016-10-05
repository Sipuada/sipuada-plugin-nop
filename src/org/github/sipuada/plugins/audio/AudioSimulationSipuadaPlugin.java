package org.github.sipuada.plugins.audio;

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
import android.javax.sdp.Connection;
import android.javax.sdp.Media;
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
    public class Session {

    	private final String localAddress;
    	private final int localPort;
    	private final String remoteAddress;
    	private final int remotePort;

    	public Session(String localAddress, int localPort,
    			String remoteAddress, int remotePort) {
			super();
			this.localAddress = localAddress;
			this.localPort = localPort;
			this.remoteAddress = remoteAddress;
			this.remotePort = remotePort;
		}

		public String getLocalAddress() {
			return localAddress;
		}

		public int getLocalPort() {
			return localPort;
		}

		public String getRemoteAddress() {
			return remoteAddress;
		}

		public int getRemotePort() {
			return remotePort;
		}

    }
    private final Map<SupportedAudioCodec, Session> flows = new HashMap<>();

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
			try {
				return includeOfferedMediaTypes(offer, localAddress);
			} catch (Throwable anyIssue) {
    			logger.error("{} could not include supported media types into offer {{}} "
					+ "in context of call invitation {} for a {} request...",
					AudioSimulationSipuadaPlugin.class.getName(), offer, callId,
					method, anyIssue);
    			return null;
			}
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
		SessionDescription offer = record.getOffer();
		record.setAnswer(answer);
		logger.info("{} received answer {{}} to offer {{}} in context of call "
			+ "invitation {}...", AudioSimulationSipuadaPlugin.class.getName(),
			answer, offer, callId);
		try {
			prepareForSessionSetup(callId, offer, answer);
		} catch (Throwable anyIssue) {
			logger.error("{} could not prepare for session setup in "
				+ "context of call invitation {}!",
				AudioSimulationSipuadaPlugin.class.getName(), callId, anyIssue);
		}
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
    		try {
        		return includeAcceptedMediaTypes(callId, answer, offer, localAddress);
    		} catch (Throwable anyIssue) {
    			logger.error("{} could not include accepted media types into answer {{}} "
					+ "to offer {{}} in context of call invitation {} for a {} request...",
					AudioSimulationSipuadaPlugin.class.getName(), answer, offer,
					callId, method, anyIssue);
    			return null;
    		}
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
//		ConnectionField connectionField = createConnectionField(localAddress);
//		sdp.setConnection(connectionField);
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

	private SessionDescription includeOfferedMediaTypes(SessionDescription offer,
			String localAddress) throws SdpException {
		Vector<String> allMediaFormats = new Vector<>();
		Vector<MediaDescription> mediaDescriptions = new Vector<>();
		for (SupportedAudioCodec audioCodec : SupportedAudioCodec.values()) {
			final String codecType = Integer.toString(audioCodec.type);
			allMediaFormats.add(codecType);
			MediaDescriptionImpl mediaDescription = new MediaDescriptionImpl();
			AttributeField rtpmapAttributeField = new AttributeField();
			rtpmapAttributeField.setName(SdpConstants.RTPMAP);
			rtpmapAttributeField.setValue(String.format(Locale.US, "%d %s",
				audioCodec.type, audioCodec.rtpmap));
			mediaDescription.addAttribute(rtpmapAttributeField);
			MediaField mediaField = new MediaField();
			Vector<String> mediaFormats = new Vector<>();
			mediaFormats.add(codecType);
			mediaField.setMediaFormats(mediaFormats);
			mediaField.setMedia("audio");
			mediaField.setMediaType("audio");
			mediaField.setProtocol(SdpConstants.RTP_AVP);
			int localAudioPort = new Random().nextInt((32767 - 16384)) + 16384;
			mediaField.setPort(localAudioPort);
			mediaDescription.setMediaField(mediaField);
			AttributeField rtcpAttribute = new AttributeField();
			rtcpAttribute.setName("rtcp");
			rtcpAttribute.setValue(Integer.toString(localAudioPort));
			mediaDescription.addAttribute(rtcpAttribute);
			AttributeField sendReceiveAttribute = new AttributeField();
			sendReceiveAttribute.setValue("sendrecv");
			mediaDescription.addAttribute(sendReceiveAttribute);
			ConnectionField connectionField = createConnectionField(localAddress);
			mediaDescription.setConnection(connectionField);
			mediaDescriptions.add(mediaDescription);
		}
		offer.setMediaDescriptions(mediaDescriptions);
		logger.info("<< {{}} codecs were declared in offer {{}} >>",
			allMediaFormats, offer);
		return offer;
	}

	@SuppressWarnings("unchecked")
	private SessionDescription includeAcceptedMediaTypes(String callId,
			SessionDescription answer, SessionDescription offer,
			String localAddress) throws SdpException {
		Vector<MediaDescription> offerMediaDescriptions = offer
			.getMediaDescriptions(false);
		if (offerMediaDescriptions == null || offerMediaDescriptions.isEmpty()) {
			return null;
		}
		Vector<String> allMediaFormats = new Vector<>();
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
								|| rtpmap.toUpperCase().trim().equals(audioCodec.rtpmap)) {
							String codecType = Integer.toString(type);
							allMediaFormats.add(codecType);
							MediaDescription cloneMediaDescription
								= new MediaDescriptionImpl();
							AttributeField rtpmapAttributeField = new AttributeField();
							rtpmapAttributeField.setName(SdpConstants.RTPMAP);
							rtpmapAttributeField.setValue(String.format(Locale.US, "%d %s",
								type, rtpmap.toUpperCase().trim()));
							cloneMediaDescription.addAttribute(rtpmapAttributeField);
							MediaField mediaField = new MediaField();
							Vector<String> mediaFormats = new Vector<>();
							mediaFormats.add(codecType);
							mediaField.setMediaFormats(mediaFormats);
							mediaField.setMedia("audio");
							mediaField.setMediaType("audio");
							mediaField.setProtocol(SdpConstants.RTP_AVP);
							int localAudioPort = new Random().nextInt
								((32767 - 16384)) + 16384;
							mediaField.setPort(localAudioPort);
							((MediaDescriptionImpl) cloneMediaDescription)
								.setMediaField(mediaField);
							AttributeField rtcpAttribute = new AttributeField();
							rtcpAttribute.setName("rtcp");
							rtcpAttribute.setValue(Integer.toString(localAudioPort));
							cloneMediaDescription.addAttribute(rtcpAttribute);
							AttributeField sendReceiveAttribute
								= new AttributeField();
							sendReceiveAttribute.setValue("sendrecv");
							cloneMediaDescription.addAttribute(sendReceiveAttribute);
							ConnectionField connectionField
								= createConnectionField(localAddress);
							cloneMediaDescription.setConnection(connectionField);
							answerMediaDescriptions.add(cloneMediaDescription);
						}
					}
				}
			}
		}
		if (answerMediaDescriptions.isEmpty()) {
			return null;
		}
		answer.setMediaDescriptions(answerMediaDescriptions);
		logger.info("<< {{}} codecs were declared in answer {{}} to {{}} >>",
			allMediaFormats, answer, offer);
		try {
			prepareForSessionSetup(callId, offer, answer);
		} catch (Throwable anyIssue) {
			logger.error("%% {} could not prepare for session setup in "
				+ "context of call invitation {}! %%",
				AudioSimulationSipuadaPlugin.class.getName(), callId, anyIssue);
		}
		return answer;
	}

	interface ExtractionCallback {

		void onConnectionInfoExtracted(String connectionAddress, int port,
				String rtpmap, int codecType);

		void onExtractionIgnored(String rtpmap, int codecType);

		void onExtractionPartiallyFailed(Throwable anyIssue);

		void onExtractionFailedCompletely(Throwable anyIssue);

	}

	abstract class ExtractionCallbackImpl implements ExtractionCallback {

		private final String role;
		private final String sdpType;

		public ExtractionCallbackImpl(String role, String sdpType) {
			this.role = role;
			this.sdpType = sdpType;
		}

		@Override
		public abstract void onConnectionInfoExtracted(String connectionAddress,
			int port, String rtpmap, int codecType);

		@Override
		public final void onExtractionIgnored(String rtpmap, int codecType) {
			logger.error("%% {{}} as {} ignored extraction of {} "
				+ "media description {{}} - code: {{}} as it "
				+ "contained no connection info. %%",
				AudioSimulationSipuadaPlugin.class.getName(),
				role, sdpType, rtpmap, codecType);
		}

		@Override
		public final void onExtractionPartiallyFailed(Throwable anyIssue) {
			logger.error("%% {{}} as {} partially failed during "
				+ "extraction of {} media description line. %%",
				AudioSimulationSipuadaPlugin.class.getName(),
				role, sdpType, anyIssue);
		}

		@Override
		public final void onExtractionFailedCompletely(Throwable anyIssue) {
			logger.error("%% {{}} as {} failed completely before "
				+ "extraction of {} media description lines. %%",
				AudioSimulationSipuadaPlugin.class.getName(),
				role, sdpType, anyIssue);
		}

	}

	private void prepareForSessionSetup(final String callId,
			final SessionDescription offer, final SessionDescription answer)
					throws SdpException {
		extractConnectionInformation(answer, new ExtractionCallbackImpl
				(roles.get(callId).toString(), "ANSWER") {

			@Override
			public void onConnectionInfoExtracted(final String answerAddress,
					final int answerPort, final String answerRtpmap,
					final int answerCodecType) {
				extractConnectionInformation(offer, new ExtractionCallbackImpl
						(CallRole.CALLER.toString(), "OFFER") {

					@Override
					public void onConnectionInfoExtracted(final String offerAddress,
							final int offerPort, final String offerRtpmap,
							final int offerCodecType) {
						if ((offerCodecType >= 0 && offerCodecType <= 34
							|| offerCodecType == answerCodecType)
							|| (offerRtpmap.toLowerCase().trim().equals
								(answerRtpmap.toLowerCase().trim()))) {
							SupportedAudioCodec supportedAudioCodec = null;
							for (SupportedAudioCodec supported
									: SupportedAudioCodec.values()) {
								if (supported.rtpmap.toLowerCase().equals
									(answerRtpmap.toLowerCase().trim())) {
									supportedAudioCodec = supported;
									break;
								}
							}
							if (supportedAudioCodec == null) {
								logger.error("%% {} found a codec match but said"
									+ " codec {} is not supported by this plugin! %%",
									AudioSimulationSipuadaPlugin.class.getName(),
									answerRtpmap + " - " + answerCodecType);
								return;
							}
							switch (roles.get(callId)) {
								case CALLER:
									flows.put(supportedAudioCodec,
										new Session(offerAddress, offerPort,
											answerAddress, answerPort));
									break;
								case CALLEE:
									flows.put(supportedAudioCodec,
										new Session(answerAddress, answerPort,
											offerAddress, offerPort));
									break;
								}
						}
					}

				});
			}

		});
	}

	@SuppressWarnings("unchecked")
	private void extractConnectionInformation(SessionDescription sdp,
			ExtractionCallback callback) {
		final Connection parentConnection;
		final String parentPort;
		final Vector<MediaDescription> mediaDescriptions;
		try {
			parentConnection = sdp.getConnection();
			parentPort = sdp.getAttribute("rtcp");
			mediaDescriptions = sdp.getMediaDescriptions(false);
		} catch (Throwable anyIssue) {
			callback.onExtractionFailedCompletely(anyIssue);
			return;
		}
		for (MediaDescription mediaDescription : mediaDescriptions) {
			Vector<AttributeField> attributeFields
				= ((MediaDescription) mediaDescription).getAttributes(false);
			for (AttributeField attributeField : attributeFields) {
				try {
					if (attributeField.getName() != null
							&& attributeField.getName().equals(SdpConstants.RTPMAP)) {
						int codecType = Integer.parseInt(attributeField
							.getValue().split(" ")[0].trim());
							String rtpmap = attributeField.getValue()
							.split(" ")[1].trim();
						final Connection connection = mediaDescription.getConnection();
						final Media media = mediaDescription.getMedia();
						final String connectionAddress;
						final int port;
						if ((parentConnection == null || parentPort == null)
								&& (connection == null || media == null)) {
							callback.onExtractionIgnored(rtpmap, codecType);
							continue;
						} else if (connection == null) {
							connectionAddress = parentConnection.getAddress();
							port = Integer.parseInt(parentPort);
						} else {
							connectionAddress = connection.getAddress();
							port = media.getMediaPort();
						}
						callback.onConnectionInfoExtracted
							(connectionAddress, port, rtpmap, codecType);
					}
				} catch (Throwable anyIssue) {
					callback.onExtractionPartiallyFailed(anyIssue);
				}
			}
		}
	}

	@Override
	public boolean performSessionSetup(String callId, SipUserAgent userAgent) {
		Record record = records.get(callId);
		SessionDescription offer = record.getOffer(), answer = record.getAnswer();
		logger.info("^^ {} performing session setup in context of call {}...\n"
			+ "Role: {{}}\nOffer: {{}}\nAnswer: {{}} ^^",
			AudioSimulationSipuadaPlugin.class.getName(),
			callId, roles.get(callId), offer, answer);
		for (SupportedAudioCodec supportedAudioCodec : flows.keySet()) {
			Session session = flows.get(supportedAudioCodec);
			logger.info("^^ Should setup a {} flow from "
				+ "{}:{} (local) to {}:{} (remote)! ^^", supportedAudioCodec,
				session.getLocalAddress(), session.getLocalPort(),
				session.getRemoteAddress(), session.getRemotePort());
		}
		return true;
	}

	@Override
	public boolean performSessionTermination(String callId) {
		records.remove(callId);
		logger.info("^^ {} performing session tear down in context of call {}... ^^",
			AudioSimulationSipuadaPlugin.class.getName(), callId);
		for (SupportedAudioCodec supportedAudioCodec : flows.keySet()) {
			Session session = flows.get(supportedAudioCodec);
			logger.info("^^ Should terminate {} flow from "
				+ "{}:{} (local) to {}:{} (remote)! ^^", supportedAudioCodec,
				session.getLocalAddress(), session.getLocalPort(),
				session.getRemoteAddress(), session.getRemotePort());
		}
		return true;
	}

}
