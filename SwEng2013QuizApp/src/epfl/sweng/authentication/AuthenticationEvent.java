package epfl.sweng.authentication;

import epfl.sweng.servercomm.ServerEvent;

public abstract class AuthenticationEvent extends ServerEvent {
	private static final long serialVersionUID = -5936292490766850403L;


	public static class GettingTokenEvent extends AuthenticationEvent {
		private static final long serialVersionUID = -4118191706020901951L;
		
		public GettingTokenEvent() {
		}
		
		public String getToken() {
			return this.getResponse();
		}
	}
	
	public static class TequilaStatusEvent extends AuthenticationEvent {
		private static final long serialVersionUID = 333326454004003586L;
		
		public TequilaStatusEvent() {
		}
		
		public String getStatus() {
			return this.getResponse();
		}
	}
	
	
	public static class GettingSessionIDEvent extends AuthenticationEvent {
		private static final long serialVersionUID = 5329326030002190038L;
		
		public GettingSessionIDEvent(String sessionID) {
		}
		
		public String getSessionID() {
			return this.getResponse();
		}
	}
}
