package vn.edu.solop;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Retains proof of a fresh scan while the same browser completes login. */
@Component
class QrTickets {
    private static final String ATTRIBUTE="solop.qrTicket";
    private final Clock clock;

    QrTickets(Clock clock) { this.clock=clock; }

    String issue(HttpServletRequest request,long sessionId,String generation) {
        var session=request.getSession();
        String loginVersion=(String)session.getAttribute("solop.loginVersion");
        var ticket=new Ticket(UUID.randomUUID().toString(),sessionId,clock.millis()+120_000,loginVersion,generation);
        session.setAttribute(ATTRIBUTE,ticket);
        return ticket.proof();
    }

    boolean valid(HttpServletRequest request,String proof,long sessionId,String generation) {
        var session=request.getSession(false);
        if(session==null || !(session.getAttribute(ATTRIBUTE) instanceof Ticket ticket))return false;
        String loginVersion=(String)session.getAttribute("solop.loginVersion");
        return ticket.sessionId()==sessionId && ticket.proof().equals(proof) && Objects.equals(generation,ticket.generation())
                && clock.millis()<ticket.expires() && loginVersion!=null
                && !Objects.equals(loginVersion,ticket.loginVersion());
    }

    void consume(HttpServletRequest request,String proof) {
        var session=request.getSession(false);
        if(session!=null && session.getAttribute(ATTRIBUTE) instanceof Ticket ticket && ticket.proof().equals(proof)) {
            session.removeAttribute(ATTRIBUTE);
        }
    }

    private record Ticket(String proof,long sessionId,long expires,String loginVersion,String generation) {}
}