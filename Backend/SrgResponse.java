import java.io.Serializable;

/*
Srg Response
Περιέχει:
τον τυχαίο αριθμό
και το secret
*/

public class SrgResponse implements Serializable {
    private int randomNumber;
    private String hash;

    public SrgResponse(int randomNumber, String hash){
        this.randomNumber = randomNumber;
        this.hash = hash;
    }

    public int getRandomNumber(){
        return randomNumber;
    }

    public String getHash(){
        return hash;
    }
}