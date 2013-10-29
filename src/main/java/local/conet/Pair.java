package local.conet;

public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
    
    @Override
    public String toString(){
    	return "[ " + first.toString() + " , " + second.toString() + " ]";
    }

    
}
