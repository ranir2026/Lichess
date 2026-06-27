package bitboardRewrite;

public class Engine {
    public long nodesSearched = 0;
    public long lastNodesSearched = 0;
    public int lastDepthReached = 0;
    public long lastElapsedMs = 0;
    public long lastNodesPerSecond = 0;
    
    public int getBestMove(int a, int b) { return 0; }

    public String getName() { return ""; }
    public double getVersion() { return 0.0; }
}
