package uk.ac.newcastle.enterprisemiddleware;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Application entry point.
 *
 * The quickstart started an H2 TCP server here so DBeaver could connect. As instructed in the coursework
 * (Part 3 warning), that has been removed because it causes problems when deployed to OpenShift; the
 * service now uses a plain in-memory H2 database (see application.properties).
 */
@QuarkusMain
public class Application {

    public static void main(String[] args) {
        Quarkus.run(args);
    }
}
