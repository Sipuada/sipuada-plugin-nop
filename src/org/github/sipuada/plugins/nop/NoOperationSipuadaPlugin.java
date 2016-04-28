package org.github.sipuada.plugins.nop;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipUserAgent;
import org.github.sipuada.plugins.SipuadaPlugin;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.socket.IceSocketWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sdp.SdpFactory;
import android.javax.sdp.SessionDescription;
import test.SdpUtils;

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
	private final Map<String, Agent> agents = new HashMap<>();

	public NoOperationSipuadaPlugin() {
		logger.info("{} sipuada plugin instantiated.", NoOperationSipuadaPlugin.class.getName());
	}

	@Override
	public SessionDescription generateOffer(String callId, RequestMethod method, String localAddress) {
		Agent agent = new Agent();
		agents.put(callId, agent);
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getByName("stun.siplogin.de");
			TransportAddress transportAddress = new TransportAddress(inetAddress, 3478, Transport.UDP);
			agent.addCandidateHarvester(new StunCandidateHarvester(transportAddress));
			IceMediaStream stream = agent.createMediaStream("audio");
			agent.createComponent(stream, Transport.UDP, 40000, 40000, 60000);
			String addressesSdp = SdpUtils.createSDPDescription(agent);
			SessionDescription offer = SdpFactory.getInstance()
					.createSessionDescriptionFromString(addressesSdp);
			records.put(callId, new Record(offer));
			logger.info("{} generating offer {{}} in context of call invitation {} for a {} request...",
					NoOperationSipuadaPlugin.class.getName(), offer, callId, method);
			return offer;
		} catch (Throwable anyException) {
			anyException.printStackTrace();
			return null;
		}
	}

	@Override
	public void receiveAnswerToAcceptedOffer(String callId, SessionDescription answer) {
		Record record = records.get(callId);
		record.setAnswer(answer);
		Agent agent = agents.get(callId);
		try {
			SdpUtils.parseSDP(agent, answer.toString());
		} catch (Exception anyException) {
			anyException.printStackTrace();
		}
		logger.info("{} received answer {{}} to offer {{}} in context of call invitation {}...",
				NoOperationSipuadaPlugin.class.getName(), answer, record.getOffer(), callId);
	}

	@Override
	public SessionDescription generateAnswer(String callId, RequestMethod method, SessionDescription offer, String localAddress) {
		Agent agent = new Agent();
		agents.put(callId, agent);
		InetAddress inetAddress;
		try {
			inetAddress = InetAddress.getByName("stun.siplogin.de");
			TransportAddress transportAddress = new TransportAddress(inetAddress, 3478, Transport.UDP);
			agent.addCandidateHarvester(new StunCandidateHarvester(transportAddress));
			IceMediaStream stream = agent.createMediaStream("audio");
			agent.createComponent(stream, Transport.UDP, 40000, 40000, 60000);
			String addressesSdp = SdpUtils.createSDPDescription(agent);
			SessionDescription answer = SdpFactory.getInstance()
					.createSessionDescriptionFromString(addressesSdp);
			records.put(callId, new Record(offer, answer));
			SdpUtils.parseSDP(agent, offer.toString());
			logger.info("{} generating answer {{}} to offer {{}} in context of call invitation {} for a {} request...",
					NoOperationSipuadaPlugin.class.getName(), answer, offer, callId, method);
			return answer;
		} catch (Throwable anyException) {
			anyException.printStackTrace();
			return null;
		}
	}

	@Override
	public boolean performSessionSetup(String callId, SipUserAgent userAgent) {
		Record record = records.get(callId);
		SessionDescription offer = record.getOffer(), answer = record.getAnswer();
		Agent agent = agents.get(callId);
		agent.addStateChangeListener(new PropertyChangeListener() {

			@Override
			public void propertyChange(PropertyChangeEvent event) {
				logger.info("We got something out of the newly established ice4j connection.");
				if (event.getSource() instanceof Agent) {
					Agent agent = (Agent) event.getSource();
					if (agent.getState().equals(IceProcessingState.TERMINATED)) {
						for (IceMediaStream stream : agent.getStreams()) {
							if (stream.getName().contains("audio")) {
								Component rtpComponent = stream.getComponent(org.ice4j.ice.Component.RTP);
								CandidatePair rtpPair = rtpComponent.getSelectedPair();
								IceSocketWrapper wrapper = rtpPair.getIceSocketWrapper();
								TransportAddress transportAddress = rtpPair.getRemoteCandidate().getTransportAddress();
								InetAddress remoteHostname = transportAddress.getAddress();
								int remotePort = transportAddress.getPort();
								logger.info("Remote peer is listening at {}:{}...", remoteHostname, remotePort);
								byte bufferSent[] = new byte[20];
								new Random().nextBytes(bufferSent);
								DatagramPacket packetToSend = new DatagramPacket(bufferSent, bufferSent.length);
								packetToSend.setAddress(remoteHostname);
								packetToSend.setPort(remotePort);
								try {
									logger.debug("Trying to send UDP test packet with seed: {}...", bufferSent);
									wrapper.send(packetToSend);
									logger.debug("UDP test packet sent!");
								} catch (IOException couldNotSendTestPacket) {
									logger.debug("UDP test packet could not be sent!");
									couldNotSendTestPacket.printStackTrace();
								}
								DatagramPacket packetToReceive = new DatagramPacket(bufferSent, 3);
								try {
									logger.debug("Now waiting for remote UDP test packet...");
									wrapper.receive(packetToReceive);
									byte bufferReceived[] = packetToReceive.getData();
									int offset = packetToReceive.getOffset();
									int length = packetToReceive.getLength();
									bufferReceived = Arrays.copyOfRange(bufferReceived, offset, offset + length);
									logger.debug("UDP test packet received with seed: {}!", bufferReceived);
								} catch (IOException couldNotReceivePacket) {
									logger.debug("UDP test packet could not be received!");
									couldNotReceivePacket.printStackTrace();
								}
							}
						}
					}
				}
			}

		});
		agent.startConnectivityEstablishment();
		logger.info("{} performing session setup in context of call (agent started={}) {}...\nOffer: {{}}\nAnswer: {{}}",
				NoOperationSipuadaPlugin.class.getName(), agent.isStarted(), callId, offer, answer);
		return true;
	}

	@Override
	public boolean performSessionTermination(String callId) {
		Agent agent = agents.get(callId);
		agent.free();
		logger.info("{} performing session tear down in context of call {}...",
				NoOperationSipuadaPlugin.class.getName(), callId);
		records.remove(callId);
		return true;
	}

//	private SessionDescription createSdp(String localIpAddress) {
//		try {
//			SessionDescription sdp = SdpFactory.getInstance().createSessionDescription();
//			String sessionName = "-";
//			long sessionId = (long) Math.random() * 100000000L;
//			long sessionVersion = sessionId;
//			OriginField originField = new OriginField();
//			originField.setUsername("NoOpSipuadaPlug-in");
//			originField.setSessionId(sessionId);
//			originField.setSessVersion(sessionVersion);
//			originField.setNetworkType(SDPKeywords.IN);
//			originField.setAddressType(SDPKeywords.IPV4);
//			originField.setAddress(localIpAddress);
//			SessionNameField sessionNameField = new SessionNameField();
//			sessionNameField.setSessionName(sessionName);
//			ConnectionField connectionField = new ConnectionField();
//			connectionField.setNetworkType(SDPKeywords.IN);
//			connectionField.setAddressType(SDPKeywords.IPV4);
//			connectionField.setAddress(localIpAddress);
//			sdp.setOrigin(originField);
//			sdp.setSessionName(sessionNameField);
//			sdp.setConnection(connectionField);
//			sdp.setMediaDescriptions(new Vector<>());
//			return sdp;
//		} catch (SdpException unexpectedException) {
//			return null;
//		}
//	}

}
