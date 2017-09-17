public class Neighbour {
    private int id;
    private String hostname;
    private String portnum;

    public Neighbour(){}

    public Neighbour(String hostname, String portnum) {
        this.hostname = hostname;
        this.portnum = portnum;
    }

    public Neighbour(int id, String hostname, String portnum) {
        this.id = id;
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