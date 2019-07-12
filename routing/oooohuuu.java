/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import routing.util.RoutingInfo;
import util.Tuple;

/**
 *
 * @author CG-DTE
 */
public class oooohuuu extends ActiveRouter{
 
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String OOOOHUUU_NS = "oooohuuu";
    public static final String MSG_COUNT_PROPERTY = OOOOHUUU_NS+"."+"copies";
    
    public static final double P_INIT=0.75;
    public static final double DEFAULT_BETA=0.25;
    public static final double GAMMA=0.98;
    public static final String SECONDS_IN_UNIT_S="secondsInTimeUnit";
    public static final String BETA_S="beta";
    
    protected int initialNrofCopies;
	protected boolean isBinary;
    
    private int secondsInTimeUnit;
    private double beta;
    private Map<DTNHost,Double>preds;
    private double lastAgeUpdate;
    
    
    
    public oooohuuu(Settings s) {
        super(s);
        Settings oooohuuuSettings = new Settings(OOOOHUUU_NS);
		
		initialNrofCopies = oooohuuuSettings.getInt(NROF_COPIES);
		isBinary = oooohuuuSettings.getBoolean( BINARY_MODE);
        
        secondsInTimeUnit = oooohuuuSettings.getInt(SECONDS_IN_UNIT_S);
        if (oooohuuuSettings.contains(BETA_S)) {
			beta = oooohuuuSettings.getDouble(BETA_S);
		}
        else{
            beta= DEFAULT_BETA;
                    }
        initPreds();
    }
    
    protected oooohuuu(oooohuuu r){
        super(r);
        this.initialNrofCopies = r.initialNrofCopies;
	this.isBinary = r.isBinary;
        
        this.secondsInTimeUnit=r.secondsInTimeUnit;
        this.beta=r.beta;
        initPreds();
        
    }
    
    private void initPreds(){
        this.preds=new HashMap<DTNHost,Double>();
    }
    
    @Override
	public Message messageTransferred(String id, DTNHost from) {
		Message msg = super.messageTransferred(id, from);
		Integer nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		
		assert nrofCopies != null : "Not a SnW message: " + msg;
		
		if (isBinary) {
			nrofCopies = (int)Math.ceil(nrofCopies*(1-getPredFor(from))/3);
		}
		else {
                    nrofCopies = 1;
		}
		
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
                
		return msg;
        }
    
    @Override 
	public boolean createNewMessage(Message msg) {
		makeRoomForNewMessage(msg.getSize());

		msg.setTtl(this.msgTtl);
		msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
		addToMessages(msg, true);
		return true;
	}
        
    @Override
	public void update() {
		super.update();
		if (!canStartTransfer() || isTransferring()) {
			return; 
		}
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		@SuppressWarnings(value = "unchecked")
		List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
		
		if (copiesLeft.size() > 0) {                  
			this.tryMessagesToConnections(copiesLeft, getConnections());
		}
	}
        
     
	protected List<Message> getMessagesWithCopiesLeft() {
		List<Message> list = new ArrayList<Message>();

		for (Message m : getMessageCollection()) {
			Integer nrofCopies = (Integer)m.getProperty(MSG_COUNT_PROPERTY);
			assert nrofCopies != null : "SnW message " + m + " didn't have " + 
				"nrof copies property!";
			if (nrofCopies > 1) {
				list.add(m);
			}
		}
		
		return list;
	}
    
    @Override
    public void changedConnection(Connection con) {
	super.changedConnection(con);
		
	if (con.isUp()) {
		DTNHost otherHost = con.getOtherNode(getHost());
		updateDeliveryPredFor(otherHost);
		updateTransitivePreds(otherHost);
	}
    }
        
     private void updateDeliveryPredFor(DTNHost host) {
		double oldValue = getPredFor(host);
		double newValue = oldValue + (1 - oldValue) * P_INIT;
		preds.put(host, newValue);
	}
     
     public double getPredFor(DTNHost host) {
		ageDeliveryPreds(); 
		if (preds.containsKey(host)) {
			return preds.get(host);
		}
		else {
			return 0;
		}
	}
	
     private void updateTransitivePreds(DTNHost host) {
		MessageRouter otherRouter = host.getRouter();
		assert otherRouter instanceof oooohuuu : "oooohuuu only works " + 
			" with other routers of same type";
		
		double pForHost = getPredFor(host); 
		Map<DTNHost, Double> othersPreds = 
			((oooohuuu)otherRouter).getDeliveryPreds();
		
		for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
			if (e.getKey() == getHost()) {
				continue; 
			}
			
			double pOld = getPredFor(e.getKey()); 
			double pNew = pOld + ( 1 - pOld) * pForHost * e.getValue() * beta;
			preds.put(e.getKey(), pNew);
		}
	}

     private void ageDeliveryPreds() {
		double timeDiff = (SimClock.getTime() - this.lastAgeUpdate) / 
			secondsInTimeUnit;
		
		if (timeDiff == 0) {
			return;
		}
		
		double mult = Math.pow(GAMMA, timeDiff);
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			e.setValue(e.getValue()*mult);
		}
		
		this.lastAgeUpdate = SimClock.getTime();
	}
     
       private Map<DTNHost, Double> getDeliveryPreds() {
		ageDeliveryPreds(); 
		return this.preds;
	} 
	
     @Override
	public RoutingInfo getRoutingInfo() {
		ageDeliveryPreds();
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = new RoutingInfo(preds.size() + 
				" delivery prediction(s)");
		
		for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
			DTNHost host = e.getKey();
			Double value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f", 
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}
        
    @Override
	protected void transferDone(Connection con) {
		Integer nrofCopies;
		String msgId = con.getMessage().getId();
		Message msg = getMessage(msgId);

		if (msg == null) { 
			return; 
		}
		nrofCopies = (Integer)msg.getProperty(MSG_COUNT_PROPERTY);
		if (isBinary) { 
			nrofCopies = nrofCopies/3;
		}
		else {
			nrofCopies--;
		}
		msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
	}

    @Override
    public MessageRouter replicate() {
        oooohuuu r = new oooohuuu(this);
		return r;
    }
    
}