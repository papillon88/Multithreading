public class Neighbour {
    private String hostname;
    private String portnum;

    public Neighbour(){}

    public Neighbour(String hostname, String portnum) {
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getPortnum() {
        return portnum;
    }

    public void setPortnum(String portnum) {
        this.portnum = portnum;
    }
}