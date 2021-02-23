import TSim.CommandException;
import TSim.SensorEvent;
import TSim.TSimInterface;
import java.util.concurrent.Semaphore;

import static java.lang.Math.abs;

public class Lab1erik {
  public Lab1eric(int speed1, int speed2) {
    new Thread(new Train(1, speed1)).start();
    new Thread(new Train(2, speed2)).start();
  }
}

// ================== TRAIN CONTROLLER ====================

/** Controls the train based on the sensors it passes */
class Train implements Runnable {
  private final int id;
  private int speed;
  private final TSimInterface tsim;

  private boolean isPrimary, wasPrimary, betweenDoubleTrack;

  public Train(int id, int speed) {
    this.id = id;
    this.speed = speed;
    betweenDoubleTrack = false;

    try {
      // Train with id 1 spawns by the upperStation
      if (id == 1)
        access.upperStation.acquire();
      else // The other at the lower
        access.lowerStation.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
      System.exit(1);
    }

    // Both trains start on the primary track of the station
    isPrimary = true;

    tsim = TSimInterface.getInstance();
  }

  @Override
  public void run() {
    try {
      // Start the trains
      tsim.setSpeed(id, speed);
      tsim.getSensor(id); // Ignore the first sensor event (which is: Station sensor, inactive)

      // The train loop
      while (true) {
        // Waits here until the train reaches a sensor event.
        SensorEvent event = tsim.getSensor(id);

        // If the train is about to leave the double track section,
        if (event.getStatus() == SensorEvent.ACTIVE && !betweenDoubleTrack) {
          betweenDoubleTrack = true; // The train is expected to arrive at another double track section.
          acquireAndMove(event.getXpos(), event.getYpos());
          tsim.getSensor(id); // Ignore the inactive event that will be triggered.
        }

        // If the train has reached a new double track section, triggered on the back flank of the sensor.
        else if (event.getStatus() == SensorEvent.INACTIVE && betweenDoubleTrack) {
          betweenDoubleTrack = false; // The train has arrived at a double track section.
          releaseHeldSections(event.getXpos(), event.getYpos());
        }
      }
    } catch (CommandException | InterruptedException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** Gain access to the sections we need to travel through/to, this is based on the sensor that was passed */
  public void acquireAndMove(int x, int y) throws CommandException, InterruptedException {
    // Either station
    if (sensors.upperStation.contains(x,y) || sensors.lowerStation.contains(x,y)) {
      stopAndTurn();
      // Since the train is just turning, not leaving a double track, it's not expected to arrive a new section.
      betweenDoubleTrack = false;
    }

    // Crossing
    else if (sensors.crossingLeft.contains(x,y) || sensors.crossingRight.contains(x,y))
      gainAccess(access.crossing);

    // Upper station turnout
    else if (sensors.upperTurnout.contains(x,y))
      moveToSection(access.upperTrack, access.midsection, switches.upperTurnout, switches.rightMidsection);

    // Right entry/exit of the midsection
    else if (sensors.rightMidsection.contains(x,y)) {
      moveToSection(access.upperTrack, access.upperStation, switches.rightMidsection, switches.upperTurnout);
    }

    // Left entry/exit of the midsection
    else if (sensors.leftMidsection.contains(x,y)) {
      moveToSection(access.lowerTrack, access.lowerStation, switches.leftMidsection, switches.lowerTurnout);
    }

    // Lower station turnout
    else if (sensors.lowerTurnout.contains(x,y)) {
      moveToSection(access.lowerTrack, access.midsection, switches.lowerTurnout, switches.leftMidsection);
    }
  }

  /** Stop for some time and change the direction that the train moves in. */
  private void stopAndTurn() throws CommandException, InterruptedException {
    // Stop
    tsim.setSpeed(id, 0);
    // Wait for 1-2 sec
    Thread.sleep(1000 + abs(speed) * 50L);
    // Move in reverse direction
    speed = -speed;
    tsim.setSpeed(id, speed);
  }

  /** Check, based on sensor locations, which sections we hold and release them. */
  public void releaseHeldSections(int x, int y) {
    // Crossing
    if (sensors.crossingLeft.contains(x, y) || sensors.crossingRight.contains(x, y))
      access.crossing.release();

    // The turnout of the upper station, next to the crossing
    else if (sensors.upperTurnout.contains(x, y))
      releaseSection(access.upperTrack, access.midsection);

    // The right side of the midsection
    else if (sensors.rightMidsection.contains(x,y))
      releaseSection(access.upperTrack, access.upperStation);

    // The left side of the midsection
    else if (sensors.leftMidsection.contains(x,y))
      releaseSection(access.lowerTrack, access.lowerStation);

    // The turnout of the lower station
    else if (sensors.lowerTurnout.contains(x,y))
      releaseSection(access.lowerTrack, access.midsection);
  }

  /**
   * Releases the semaphore of the held single track section and double track section
   * if the train held the primary track.
   */
  public void releaseSection(Semaphore singleTrack, Semaphore doubleTrack) {
    singleTrack.release();
    // Release the double track
    if (wasPrimary) doubleTrack.release();
  }

  /** Travels (from a double section) to another double track section by passing through a single track section */
  public void moveToSection(Semaphore through, Semaphore to, Switch fromSwitch,
                                  Switch toSwitch) throws CommandException, InterruptedException {
    // Try to access the "through"-section (single track), wait until we get access.
    gainAccess(through);

    // Make sure that we can leave the current section,
    fromSwitch.selectTrack(wasPrimary = isPrimary);

    // Try to access the primary track of the "to" (double track)
    toSwitch.selectTrack(isPrimary = to.tryAcquire());
  }

  /** Stops and waits before entering the section unless it's free */
  public void gainAccess(Semaphore sem) throws CommandException, InterruptedException {
    tsim.setSpeed(id, 0);
    sem.acquire();
    tsim.setSpeed(id, speed);
  }
}

// ================== MAP REPRESENTATION ====================

/**
 * These are all the sensors that have been placed on the map.
 */
class sensors {
  static SensorPair upperStation    = new SensorPair(15, 3, 15, 5);
  static SensorPair crossingRight   = new SensorPair(6, 7, 8, 5);
  static SensorPair crossingLeft    = new SensorPair(9, 8, 10, 7);
  static SensorPair upperTurnout    = new SensorPair(15, 7, 16, 8);
  static SensorPair rightMidsection = new SensorPair(13, 9, 14, 10);
  static SensorPair leftMidsection  = new SensorPair(6, 9, 5, 10);
  static SensorPair lowerTurnout    = new SensorPair(5, 11, 3, 13);
  static SensorPair lowerStation    = new SensorPair(15, 11, 15, 13);
}

/**
 * These represents the sections, more specifically the access to a section.
 * In the case of sections where multiple trains can access it (parallel tracks),
 * the access keeps track of who's taking the standard case track (usually the upper one).
 */
class access {
  static volatile Semaphore upperStation = new Semaphore(1);
  static volatile Semaphore crossing     = new Semaphore(1);
  static volatile Semaphore upperTrack   = new Semaphore(1);
  static volatile Semaphore midsection   = new Semaphore(1);
  static volatile Semaphore lowerTrack   = new Semaphore(1);
  static volatile Semaphore lowerStation = new Semaphore(1);
}

/**
 * These are the turnout switches that are placed on the map.
 */
class switches {
  static volatile Switch upperTurnout    = new Switch(17,7, TSimInterface.SWITCH_RIGHT);
  static volatile Switch rightMidsection = new Switch(15,9, TSimInterface.SWITCH_RIGHT);
  static volatile Switch leftMidsection  = new Switch(4,9, TSimInterface.SWITCH_LEFT);
  static volatile Switch lowerTurnout    = new Switch(3,11, TSimInterface.SWITCH_LEFT);
}

// ================== HELPER CLASSES ====================

/** Represents a pair of sensors placed parallel in a double track section. */
class SensorPair {
  private final int xPos1, yPos1, xPos2, yPos2;
  public SensorPair(int x1, int y1, int x2, int y2) {
    xPos1 = x1; yPos1 = y1;
    xPos2 = x2; yPos2 = y2;
  }

  /** Returns true if the provided coordinates has a sensor on them. */
  public boolean contains(int x, int y) {
    return xPos1 == x && yPos1 == y || xPos2 == x && yPos2 == y;
  }
}

/**
 * Represents a switch on the maps and its primary track. The primary track is
 * the first choice when a train tries to access double track section.
 */
class Switch {
  private final int x, y, primary, secondary;

  public Switch(int x, int y, int primary) {
    this.x = x;
    this.y = y;
    this.primary = primary;
    secondary = primary == TSimInterface.SWITCH_LEFT ? TSimInterface.SWITCH_RIGHT : TSimInterface.SWITCH_LEFT;
  }

  /** If isPrimary is true the primary track is selected, if false then the secondary is selected. */
  public void selectTrack(boolean toPrimary) throws CommandException {
    TSimInterface.getInstance().setSwitch(x,y, toPrimary ? primary : secondary);
  }
}
