package ro.pippo.demo.csscompilers;

import ro.pippo.core.Pippo;

/**
 * Created by Daniel Jipa on 20.06.2015.
 */
public class CssCompilerDemo {
    public static void main(String[] args) {
        Pippo pippo = new Pippo(new CssCompilerApplication());
        pippo.start();
    }

}
