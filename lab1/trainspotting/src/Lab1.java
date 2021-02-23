
import TSim.*;

        import java.util.HashMap;
        import java.util.List;
        import java.util.Map;
        import java.util.concurrent.Semaphore;

        import static java.util.Arrays.asList;


public class Lab1 {


    enum SensorName {
        CROSSING_N, CROSSING_W, CROSSING_S, CROSSING_E,
        NORTH_SPLIT_NW, NORTH_SPLIT_SW, NORTH_SPLIT_E,
        MID_E_SPLIT_E, MID_E_SPLIT_NW, MID_E_SPLIT_SW,
        MID_W_SPLIT_NE, MID_W_SPLIT_SE, MID_W_SPLIT_W,
        SOUTH_SPLIT_W, SOUTH_SPLIT_NE, SOUTH_SPLIT_SE,
        NORTH_STATION_N, NORTH_STATION_S, SOUTH_STATION_N, SOUTH_STATION_S

    }

    enum SwitchName {
        NORTH, SOUTH, MID_EAST, MID_WEST
    }

    enum SemaphoreName {
        CROSSING, NORTH, EAST, WEST, MID, SOUTH
    }

    public Map<SensorName, List<Integer>> sensors = new HashMap<SensorName, List<Integer>>();
    public Map<SwitchName, List<Integer>> switches = new HashMap<SwitchName, List<Integer>>();
    public Map<SemaphoreName, Semaphore> semaphores = new HashMap<SemaphoreName, Semaphore>();
    public Map<List<Integer>, SensorName> sensorsInversed = new HashMap<List<Integer>, SensorName>();


    public Lab1(Integer speed1, Integer speed2) {


        TSimInterface tsi = TSimInterface.getInstance();

        // Putting all the things in maps
        semaphores.put(SemaphoreName.CROSSING, new Semaphore(1));
        semaphores.put(SemaphoreName.NORTH, new Semaphore(0));
        semaphores.put(SemaphoreName.SOUTH, new Semaphore(0));
        semaphores.put(SemaphoreName.EAST, new Semaphore(1));
        semaphores.put(SemaphoreName.WEST, new Semaphore(1));
        semaphores.put(SemaphoreName.MID, new Semaphore(1));


        switches.put(SwitchName.NORTH, asList(17, 7));
        switches.put(SwitchName.MID_EAST, asList(15, 9));
        switches.put(SwitchName.MID_WEST, asList(4, 9));
        switches.put(SwitchName.SOUTH, asList(3, 11));


        sensors.put(SensorName.CROSSING_N, asList(11, 5));
        sensors.put(SensorName.CROSSING_W, asList(6, 3));
        sensors.put(SensorName.CROSSING_S, asList(11, 8));
        sensors.put(SensorName.CROSSING_E, asList(11, 7));

        sensors.put(SensorName.NORTH_SPLIT_NW, asList(14, 7));
        sensors.put(SensorName.NORTH_SPLIT_SW, asList(14, 8));
        sensors.put(SensorName.NORTH_SPLIT_E, asList(19, 8));

        sensors.put(SensorName.MID_E_SPLIT_E, asList(18, 9));
        sensors.put(SensorName.MID_E_SPLIT_NW, asList(11, 9));
        sensors.put(SensorName.MID_E_SPLIT_SW, asList(11, 10));

        sensors.put(SensorName.MID_W_SPLIT_NE, asList(8, 9));
        sensors.put(SensorName.MID_W_SPLIT_SE, asList(8, 10));
        sensors.put(SensorName.MID_W_SPLIT_W, asList(1, 9));

        sensors.put(SensorName.SOUTH_SPLIT_NE, asList(8, 11));
        sensors.put(SensorName.SOUTH_SPLIT_SE, asList(8, 13));
        sensors.put(SensorName.SOUTH_SPLIT_W, asList(1, 11));

        sensors.put(SensorName.NORTH_STATION_N, asList(14, 3));
        sensors.put(SensorName.NORTH_STATION_S, asList(14, 5));
        sensors.put(SensorName.SOUTH_STATION_N, asList(14, 11));
        sensors.put(SensorName.SOUTH_STATION_S, asList(14, 13));


        for (Map.Entry<SensorName, List<Integer>> entry : sensors.entrySet()) {
            sensorsInversed.put(entry.getValue(), entry.getKey());
        }

        Train train1 = null;
        Train train2 = null;

        //Initializing the trains
        try {
            train1 = new Train(1, speed1, tsi, SensorName.NORTH_STATION_N, SemaphoreName.NORTH);
            train2 = new Train(2, speed2, tsi, SensorName.SOUTH_STATION_N, SemaphoreName.SOUTH);
        } catch (CommandException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //Starting trains
        train1.start();
        train2.start();


    }


    class Train extends Thread {

        Integer id;
        Integer speed;
        //Comparative direction, 1 is starting direction, -1 is the opposite.
        //Because of how the track is designed the trains can only change direction when at a complete stop.
        Integer direction = 1;
        TSimInterface tsi;
        SensorName lastSensor;
        Integer maxSpeed = 25;
        SemaphoreName lastSemaphore;
        SemaphoreName currentSemaphore;



        Train(Integer id, Integer startSpeed, TSimInterface tsi, SensorName startSensor, SemaphoreName startSemaphore) throws CommandException, InterruptedException {
            this.id = id;

            this.speed=maxSpeedCheck(startSpeed);
            this.tsi = tsi;
            this.lastSensor = startSensor;
            this.lastSemaphore = startSemaphore;
            this.currentSemaphore = lastSemaphore;
        }

        Integer maxSpeedCheck(Integer speed){
            if (speed<=maxSpeed){
                return speed;
            }
            else{
                return maxSpeed;
            }
        }

        private void goForward() throws CommandException {
            tsi.setSpeed(id, speed*direction);
        }


        /**
         * Sets a given switch to a given direction.
         * @param switchName The name of the switch to be set.
         * @param direction The direction from TSimInterface.
         * @throws CommandException
         */
        private void setSwitch(SwitchName switchName, int direction) throws CommandException {

            List<Integer> switchPos = switches.get(switchName);
            tsi.setSwitch(switchPos.get(0), switchPos.get(1), direction);
        }

        /**
         * Sets the trains speed to 0 until the semaphore gives it a passs.
         * @param semaphoreName
         * @throws CommandException
         * @throws InterruptedException
         */
        private void waitForPass(SemaphoreName semaphoreName) throws CommandException, InterruptedException {
            Semaphore semaphore = semaphores.get(semaphoreName);
            tsi.setSpeed(id, 0);
            semaphore.acquire();
            updateSemaphores(semaphoreName);
            tsi.setSpeed(id, this.direction * speed);
        }

        /**
         * Same as waitForPass(SemaphoreName semaphoreName) but it also sets a switch to the method's given value after the permit is acquired.
         * @param semaphoreName
         * @param switchName
         * @param direction
         * @throws CommandException
         * @throws InterruptedException
         */
        private void waitForPass(SemaphoreName semaphoreName, SwitchName switchName, int direction) throws CommandException, InterruptedException {
            Semaphore semaphore = semaphores.get(semaphoreName);
            tsi.setSpeed(id, 0);
            semaphore.acquire();
            updateSemaphores(semaphoreName);
            setSwitch(switchName, direction);
            tsi.setSpeed(id, this.direction * speed);

        }

        /**
         * Releases a permit from a semaphore.
         * @param semaphoreName
         */
        private void releasePass(SemaphoreName semaphoreName) {
            Semaphore semaphore = semaphores.get(semaphoreName);
            if (semaphore.availablePermits() == 0 && (semaphoreName.equals(lastSemaphore) || semaphoreName.equals(SemaphoreName.CROSSING))) {
                semaphore.release();
                lastSemaphore = currentSemaphore;
            }
        }


        private boolean semaphoreHasAvailablePermits(SemaphoreName semaphoreName) {
            boolean gotPermit = semaphores.get(semaphoreName).tryAcquire();
            updateSemaphores(semaphoreName);
            return gotPermit;
        }

        private void updateSemaphores(SemaphoreName semaphoreName){
            if (semaphoreName!=SemaphoreName.CROSSING){
                lastSemaphore = currentSemaphore;
                currentSemaphore = semaphoreName;
            }
        }

        private void handleSensorEvent(SensorEvent sensorEvent) throws CommandException, InterruptedException {
            boolean active = sensorEvent.getStatus() == SensorEvent.ACTIVE;
            List<Integer> sensorPos = asList(sensorEvent.getXpos(), sensorEvent.getYpos());

            SensorName sensorName = sensorsInversed.get(sensorPos);
            if (active) {
                switch (sensorName) {
                    //CROSSING
                    case CROSSING_E:
                        if (lastSensor != SensorName.CROSSING_W) {
                            waitForPass(SemaphoreName.CROSSING);
                        } else {
                            releasePass(SemaphoreName.CROSSING);
                        }
                        break;
                    case CROSSING_W:
                        if (lastSensor == SensorName.CROSSING_E) {
                            releasePass(SemaphoreName.CROSSING);
                        } else {
                            waitForPass(SemaphoreName.CROSSING);
                        }
                        break;
                    case CROSSING_N:
                        if (lastSensor != SensorName.CROSSING_S) {
                            waitForPass(SemaphoreName.CROSSING);
                        } else {
                            releasePass(SemaphoreName.CROSSING);
                        }
                        break;
                    case CROSSING_S:
                        if (lastSensor != SensorName.CROSSING_N) {
                            waitForPass(SemaphoreName.CROSSING);
                        } else {
                            releasePass(SemaphoreName.CROSSING);
                        }
                        break;

                    //NORTH JUNCTION
                    case NORTH_SPLIT_E:
                        if (lastSensor == SensorName.MID_E_SPLIT_E) {
                            if (semaphoreHasAvailablePermits(SemaphoreName.NORTH)) {
                                setSwitch(SwitchName.NORTH, TSimInterface.SWITCH_RIGHT);
                                goForward();
                            } else {
                                setSwitch(SwitchName.NORTH, TSimInterface.SWITCH_LEFT);
                            }
                        } else if (lastSensor == SensorName.NORTH_SPLIT_NW) {
                            releasePass(SemaphoreName.NORTH);
                        }
                        break;

                    case NORTH_SPLIT_NW:
                        if (lastSensor != SensorName.NORTH_SPLIT_E) {
                            waitForPass(SemaphoreName.EAST, SwitchName.NORTH, TSimInterface.SWITCH_RIGHT);
                        } else
                            releasePass(SemaphoreName.EAST);
                        break;
                    case NORTH_SPLIT_SW:
                        if (lastSensor == SensorName.NORTH_SPLIT_E) {
                            releasePass(SemaphoreName.EAST);
                        } else {
                            waitForPass(SemaphoreName.EAST, SwitchName.NORTH, TSimInterface.SWITCH_LEFT);
                        }
                        break;

                    //EAST PITSTOP
                    case MID_E_SPLIT_E:
                        if (lastSensor == SensorName.NORTH_SPLIT_E) {
                            if (semaphoreHasAvailablePermits(SemaphoreName.MID)) {
                                setSwitch(SwitchName.MID_EAST, TSimInterface.SWITCH_RIGHT);
                            } else {
                                setSwitch(SwitchName.MID_EAST, TSimInterface.SWITCH_LEFT);
                            }
                        } else if (lastSensor == SensorName.MID_E_SPLIT_NW) {
                            releasePass(SemaphoreName.MID);
                        }
                        break;
                    case MID_E_SPLIT_NW:
                        if (lastSensor != SensorName.MID_E_SPLIT_E) {
                            waitForPass(SemaphoreName.EAST, SwitchName.MID_EAST, TSimInterface.SWITCH_RIGHT);
                        } else {
                            releasePass(SemaphoreName.EAST);
                        }
                        break;
                    case MID_E_SPLIT_SW:
                        if (lastSensor == SensorName.MID_E_SPLIT_E) {
                            releasePass(SemaphoreName.EAST);
                        } else {
                            waitForPass(SemaphoreName.EAST, SwitchName.MID_EAST, TSimInterface.SWITCH_LEFT);
                        }
                        break;

                    //WEST PITSTOP
                    case MID_W_SPLIT_NE:
                        if (lastSensor != SensorName.MID_W_SPLIT_W) {
                            waitForPass(SemaphoreName.WEST, SwitchName.MID_WEST, TSimInterface.SWITCH_LEFT);
                        } else {
                            releasePass(SemaphoreName.WEST);
                        }
                        break;
                    case MID_W_SPLIT_SE:
                        if (lastSensor != SensorName.MID_W_SPLIT_W) {
                            waitForPass(SemaphoreName.WEST, SwitchName.MID_WEST, TSimInterface.SWITCH_RIGHT);
                        } else {
                            releasePass(SemaphoreName.WEST);
                        }
                        break;
                    case MID_W_SPLIT_W:
                        if (lastSensor == SensorName.SOUTH_SPLIT_W) {
                            if (semaphoreHasAvailablePermits(SemaphoreName.MID)) {
                                setSwitch(SwitchName.MID_WEST, TSimInterface.SWITCH_LEFT);
                                goForward();
                            } else {
                                setSwitch(SwitchName.MID_WEST, TSimInterface.SWITCH_RIGHT);
                            }
                        } else if (lastSensor == SensorName.MID_W_SPLIT_NE) {
                            releasePass(SemaphoreName.MID);
                        }
                        break;

                    //SOUTH JUNCTION
                    case SOUTH_SPLIT_W:
                        if (lastSensor == SensorName.MID_W_SPLIT_W) {
                            if (semaphoreHasAvailablePermits(SemaphoreName.SOUTH)) {
                                setSwitch(SwitchName.SOUTH, TSimInterface.SWITCH_LEFT);
                                goForward();
                            } else {
                                setSwitch(SwitchName.SOUTH, TSimInterface.SWITCH_RIGHT);
                            }
                        } else if (lastSensor == SensorName.SOUTH_SPLIT_NE) {
                            releasePass(SemaphoreName.SOUTH);
                        }
                        break;
                    case SOUTH_SPLIT_NE:
                        if (lastSensor == SensorName.SOUTH_SPLIT_W) {
                            releasePass(SemaphoreName.WEST);
                        } else {
                            waitForPass(SemaphoreName.WEST, SwitchName.SOUTH, TSimInterface.SWITCH_LEFT);
                        }
                        break;
                    case SOUTH_SPLIT_SE:
                        if (lastSensor == SensorName.SOUTH_SPLIT_W) {
                            releasePass(SemaphoreName.WEST);
                        } else {
                            waitForPass(SemaphoreName.WEST, SwitchName.SOUTH, TSimInterface.SWITCH_RIGHT);
                        }
                        break;


                    //NORTH STATION
                    case NORTH_STATION_N:
                        if (lastSensor == SensorName.CROSSING_W) {
                            waitAtStation();
                        }
                        break;
                    case NORTH_STATION_S:
                        if (lastSensor == SensorName.CROSSING_N) {
                            waitAtStation();
                        }
                        break;

                    //SOUTH STATION
                    case SOUTH_STATION_N:
                        if (lastSensor == SensorName.SOUTH_SPLIT_NE) {
                            waitAtStation();
                        }
                        break;
                    case SOUTH_STATION_S:
                        if (lastSensor == SensorName.SOUTH_SPLIT_SE) {
                            waitAtStation();
                        }
                        break;


                }

                //Store which sensor was just handled.
                lastSensor = sensorName;

            }

        }

        private void changeDirection() {
            direction *= -1;
        }

        /**
         * Stops the train, sleeps the thread, changes the trains direction and let's it take off again.
         */
        private void waitAtStation() {
            try {
                tsi.setSpeed(id, 0);
                sleep(1000 + (20 * speed));
                changeDirection();
                tsi.setSpeed(id, this.direction * speed);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (CommandException e) {
                e.printStackTrace();
            }
        }


        public void run() {
            try {
                tsi.setSpeed(id, maxSpeedCheck(this.speed));
            } catch (CommandException e) {
                e.printStackTrace();
            }
            while (!this.isInterrupted()) {
                try {
                    handleSensorEvent(tsi.getSensor(this.id));
                } catch (CommandException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

        }
    }
}