/**
 * Author:      Jose Jimenez
 * File Name:   DashboardGUI.java   
 * Description: Vehicle Dashboard Graphical User Interface.
 */

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.BorderLayout;
import javax.swing.Timer;
import java.awt.event.*;
import java.util.Vector;
import javax.swing.event.PopupMenuListener;
import javax.swing.event.PopupMenuEvent;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;

/**
 * Dashboard interface with an asynchronous port reciever.
 */
public class DashboardGUI implements ActionListener, PopupMenuListener {

  private static final boolean DEBUG_ON = true;
  /* color pallette */
  private static final Color LIGHT_BLACK     = new Color( 32, 32, 32 );
  private static final Color NEON_GREEN      = new Color( 0, 128, 0 );
  private static final Color NEON_BLUE = NEON_GREEN;
  //private static final Color NEON_BLUE       = new Color(0, 0, 255);
  private static final Color DARK_NEON_GREEN = new Color( 0, 100, 0 );
  private static final Color METALLIC_BLACK  = new Color( 50, 50, 50 );
  private static final Color NEON_RED        = new Color( 255, 0, 0 );
  private static final Color DARK_NEON_RED   = new Color( 200, 0, 0 );
  private static final String TRANSMISSION_LABEL = "P  R  D";
  private static final String MPH_LABEL          = "MPH";

  private static SerialRoute serialComm = SerialRoute.getInstance();

  public static final int MAX_SPEED = 120;
  public static final int MIN_SPEED = 0;
  public static final int MIN_ANGLE = -150;
  public static final int MAX_ANGLE = 150;
  private static final int DELAY    = 100;

  /* Transmission / speedometer needle angle */
  private enum Transmission { PARK, REVERSE, DRIVE }
  private int currentTheta = MIN_ANGLE;
  private Transmission transmission_state = Transmission.PARK;
  
  /* rendering panel with enabled double buffering */
  private JPanel renderPanel;
  private JPanel northPanel;
  private Timer timer;
  private JComboBox<String> portComboBox;
  private JButton debugButton;

  boolean increasing = true;
  int speed = MIN_SPEED;
  double batteryPercentage = 0.75;

  boolean locked = false;

  /**
   *  Constructor sets up the window behavior and graphics of the Dashboard GUI.
   */
  public DashboardGUI() {
    begin();
  }

  /**
   * Initial setup of GUI components.
   * @return Nothing.
   */
  private void begin() {
    JFrame main_frame = new JFrame( "Dashboard Graphics" );
    main_frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    //if( !DEBUG_ON ) main_frame.setUndecorated(true);
    GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];
    if( !DEBUG_ON ) device.setFullScreenWindow(main_frame);
    main_frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
    main_frame.setBackground( Color.BLACK );
    main_frame.setResizable( true );

    northPanel  = new JPanel();
    renderPanel = new RenderPanel();
    renderPanel.setBackground( Color.BLACK );

    /* set layout for buttons/scrolls */
    northPanel.setLayout( new BoxLayout(northPanel, BoxLayout.LINE_AXIS) );

    /* background color of button panels */
    northPanel.setBackground( Color.BLACK );

    /* add content to north panel */
    Vector<String> portList = serialComm.getPortList();
    portList.add( 0, "Disconnected" );
    portComboBox = new JComboBox<String>( portList );
    portComboBox.setMaximumSize( portComboBox.getPreferredSize() );
    portComboBox.setSelectedItem( 0 );

    debugButton = new JButton( "Debug" );
    debugButton.setVisible( DEBUG_ON );

    debugButton.addActionListener( this );
    portComboBox.addActionListener( this );
    serialComm.addActionListener(this);
    /* port combo box menu interaction events */
    portComboBox.addPopupMenuListener( this );

    northPanel.add( debugButton );
    northPanel.add( Box.createHorizontalGlue() );
    northPanel.add( portComboBox );

    /* add rendering panel to jframe */
    Container contentPane = main_frame.getContentPane();
    contentPane.setBackground( Color.BLACK );
    contentPane.add( northPanel, BorderLayout.NORTH );
    contentPane.add( renderPanel, BorderLayout.CENTER );
    contentPane.validate();

    main_frame.setVisible( true );
    timer = new Timer( DELAY, this );
    timer.start();
  }

  /**
   * Asynchronous tasks are being triggered.
   * @param evt event that asynchronously got triggered.
   */
  public void actionPerformed( ActionEvent evt ) {
    if( evt.getSource() == serialComm ) {
      /* user sending data to dashboard gui */
      handleSerialRouteEvent( evt );
    }
    if( evt.getSource() == timer ) {
      /* animation handling fps */
      renderPanel.repaint();
    }
    if( evt.getSource() == portComboBox ) {
      /* user attempting to connect device */
      handlePortComboBoxEvent( evt );
    }
    if( evt.getSource() == debugButton ) {
      /* debuging */
      handleDebugEvent( evt );
    }
  }

  /**
   * Set speed of speedometer based on device input.
   * @param evt Event triggered by GUI recieving data from serial port.
   */
  private void handleSerialRouteEvent( ActionEvent evt ) {
    SerialRouteEvent serialEvt = (SerialRouteEvent) evt;
    String data = serialEvt.getReceivedMessage();
    if( isSpeedData(data) ) {
      /* input is speed */
      setSpeed( data );
    }
    else if( isTransmissionData(data) ) {
      setTransmission( data );
    }
    else if( isBatteryPercentageData(data) ) {
      setBatteryPercentage( data );
    }
    else if( isLockedData(data) ) {
      setLocked( data );
    }
  }

  /**
   * UI for connecting device to dashboard.
   * @param evt Event triggered from user changing device to connect to.
   */
  private void handlePortComboBoxEvent( ActionEvent evt ) {
      String selectedPort = portComboBox.getSelectedItem().toString();
      String noPort = portComboBox.getItemAt( 0 ).toString();

      if( selectedPort.equals("ttyAMA0") ) {
        /* inhibit weird rasberry pi behavior */
        selectedPort = "null";
      }

      if( serialComm.connectTo( selectedPort ) ) {
        System.out.println( "Connected: " + selectedPort );
      }
      else if( selectedPort.equals(noPort) ) {
        System.out.println( "Disconnected." );
        serialComm.disconnect();
	      setSpeed( 0 );
      }
      else {
        System.out.println( "Failed Connection: " + selectedPort );
        serialComm.disconnect();
	      setSpeed( 0 );
        portComboBox.setSelectedItem( 0 );
      }
  }

  /**
   * Asynchronous update serial port list in combo box upon user interaction.
   * @return Nothing.
   */
  @Override
  public void popupMenuWillBecomeVisible( PopupMenuEvent evt ) {
    updatePortComboBox();
  }
  /* required override for PopupMenuListener interface inheritance */
  @Override
  public void popupMenuWillBecomeInvisible( PopupMenuEvent evt ) {}
  @Override
  public void popupMenuCanceled( PopupMenuEvent evt ) {}

  /**
   * Updates the available port list in combo box.
   * @return Nothing.
   */
  private void updatePortComboBox() {
    /* current available ports */
    Vector<String> portList = serialComm.getPortList();
    portList.insertElementAt(portComboBox.getItemAt(0), 0); /* diconnected port */

    /* currently connected device got disconnected */
    String selectedPort = portComboBox.getSelectedItem().toString();
    if( !portList.contains(selectedPort) ) {
      /* reflect disconnection in front end */
      serialComm.disconnect();
      portComboBox.setSelectedIndex( 0 );
      System.out.println("Disconnected.");
    }

    /* remove items */
    for( int index = 0; index < portComboBox.getItemCount(); index++ ) {
      String item = portComboBox.getItemAt(index);
      if( !portList.contains(item) ) {
        portComboBox.removeItemAt(index);
        index--; /* new item will shift into current position */
      }
    }

    /* list is up to date */
    if( portComboBox.getItemCount() ==  portList.size() ) return;

    /* add items */
    for( int index = 0; index < portList.size(); index++ ) {
      /* utilizing strict item order */
      String port = portList.elementAt( index );
      String item = portComboBox.getItemAt( index );
      if( item == null ) {
        portComboBox.addItem( port );
      }
      else if( !item.equals(port) ) {
        portComboBox.insertItemAt( port, index );
      }
    }
  }


  /**
   * For debugging purposes.
   * @param evt Event triggered from debug button.
   * @return Nothing.
   */
  private void handleDebugEvent( ActionEvent evt ) {
    System.out.println("debug event triggered");

  }

  /**
   * Smooth graphic interface for dashboard.
   */
  private class RenderPanel extends JPanel {
    /* speedometer needle and center point of GUI */
    private RoundRectangle2D needle;
    private Point center;

    /* speedometer dimensions */
    private int outer_diameter;
    private int outer_radius;
    private int diameter;
    private int radius;   
    private int rim_diameter; 
    private int rim_radius;
    private int inner_diameter; 
    private int inner_radius; 
    private int needle_offset;
    private int needle_width;

    /* speedometer label dimensions */
    private int tensWidth; 
    private int tensOffset;
    private double arcAngle;

    /* transmission specs */
    private Font transmissionFont;
    private int transmission_width;
    private int MPH_offset;

    /* battery percentage specs */
    private Font batteryPercentageFont;

    /**
     * Render Dashboard graphics on display screen.
     * @param g Graphics reference to screen.
     * @return Nothing.
     */
    @Override
    protected void paintComponent( Graphics g ) {
      super.paintComponent( g );
      render( g );
    }

    /**
     * Draw components on screen.
     * @return Nothing.
     */
    private void render( Graphics g ) {
      drawSpeedometer( g );
      //drawBatteryCapacity( g );
      if( locked ) {
        drawLock( g );
      }
    }

    public void drawLock( Graphics g) {
      center = new Point( getWidth() / 2, getHeight() / 2 );
      double theta = Math.PI / 6;

      int lock_side      = (int)(double)(0.15 * outer_radius);
      int shackle_height = (int)(double)(0.65 * lock_side);
      int shackle_width  = (int)(double)( 0.9 * lock_side );
      int shackle_guage  = (int)(double)(0.1 * lock_side);
      int lock_x = (int)(double)( 0.5 * (center.x - outer_radius * Math.cos(theta) - lock_side) );
      int lock_y = (int)(double)( center.y - outer_radius * Math.sin(theta) - lock_side);
      int shackle_x = (int)(double)( lock_x + 0.5*(lock_side - shackle_width) );
      int shackle_y = (int)(double)( lock_y - shackle_height );

      g.setColor( Color.WHITE );
      /* lock shackle */
      g.fillOval(shackle_x, shackle_y, shackle_width, 2 * shackle_height);
      g.setColor( Color.BLACK );
      g.fillOval(shackle_x + shackle_guage, shackle_y + shackle_guage, shackle_width - 2*shackle_guage, 2 * (shackle_height - shackle_guage));
      /* lock body */
      g.drawRoundRect(lock_x, lock_y, lock_side, lock_side, 6, 6 );
      g.setColor( Color.WHITE);
      g.fillRoundRect(lock_x, lock_y, lock_side, lock_side, 6, 6 );
    }

    /**
     * Draws Battery Capacity of go kart motor battery pack.
     * @param g An abstract canvas for animations.
     * @return Nothing.
     */
    private void drawBatteryCapacity( Graphics g ) {
      center = new Point( getWidth() / 2, getHeight() / 2 );
      double BATTERY_GUAGE = 0.5 * diameter / Math.min( getWidth(), getHeight() );

      int battery_width   = (int)(double)(BATTERY_GUAGE * Math.min(0.5 * diameter, getWidth() - center.x - outer_radius));
      int battery_height  = (int)(double)(0.4 * battery_width);
      int terminal_width  = (int)(double)(0.05 * battery_width);
      int terminal_height = (int)(double)(0.33 * battery_height);
      int battery_x  = (int)(double)(center.x + outer_radius + 0.5 * BATTERY_GUAGE * (getWidth() - center.x - outer_radius));
      int battery_y  = (int)(double)(getHeight() - 2 * battery_height);
      int terminal_x = (int)(double)(battery_x + battery_width);
      int terminal_y = (int)(double)(battery_y + 0.33 * battery_height);
      batteryPercentageFont = new Font( Font.SANS_SERIF, Font.PLAIN, (int)(0.4*battery_height));

      g.setColor( Color.WHITE );
      g.drawRoundRect(battery_x, battery_y, battery_width, battery_height, 6, 6);
      g.fillArc(terminal_x, terminal_y, terminal_width, terminal_height, -90, 180);
      g.fillRoundRect(battery_x + 2, battery_y + 2, (int)(batteryPercentage * (battery_width - 4)), battery_height - 4, 6, 6);

      g.setFont( batteryPercentageFont );
      int max_width = g.getFontMetrics().stringWidth( "100%" );
      g.drawString( Integer.toString((int)(batteryPercentage * 100)) + "%", battery_x - max_width, battery_y + battery_height - (int)(0.3 * battery_height));
    } 

    /**
     *  Draws the speedometer design.
     *  @param g An abstract canvas for animations.
     *  @return Nothing.
     */
    private void drawSpeedometer( Graphics g ) {
      center = new Point( getWidth() / 2, getHeight() / 2 );

      double GUAGE_SIZE = 1.3;

      outer_diameter = (int)(double)(0.75 * GUAGE_SIZE * Math.min( getWidth(), getHeight() ));
      outer_radius   = (int)(double)(0.5 * outer_diameter);
      diameter       = (int)(double)(0.7 * GUAGE_SIZE * Math.min( getWidth(), getHeight() ));
      radius         = (int)(double)(0.5 * diameter);
      rim_diameter   = (int)(double)(0.69 * GUAGE_SIZE * Math.min( getWidth(), getHeight() ));
      rim_radius     = (int)(double)(0.5 * rim_diameter);
      inner_diameter = (int)(double)(0.05 * GUAGE_SIZE * Math.min( getWidth(), getHeight() ));
      inner_radius   = (int)(double)(0.5 * inner_diameter);
      needle_offset  = (int)(double)(0.1 * rim_radius);
      needle_width   = (int)(double)(0.2 * inner_radius);
      tensWidth      = (int)(double)(0.5 * inner_radius);
      tensOffset     = (int)(double)(0.5 * tensWidth);
      int markHeight       = (int)(double)(0.4 * needle_offset);
      int markWidthOffset  = (int)(double)(0.2 * markHeight);
      int MPH_fontsize     = (int)(double)(0.06 * rim_radius);
      MPH_offset           = (int)(double)(0.25 * rim_radius);
      Font speedometerFont = new Font( Font.SANS_SERIF, Font.PLAIN, MPH_fontsize );
      transmissionFont     = new Font( Font.SANS_SERIF, Font.BOLD, (int)(1.5 * MPH_fontsize) );

      if( needle_width == 0 ) needle_width = 1;

      /* larger speedometer background */
      g.setColor( LIGHT_BLACK );
      g.fillOval( center.x - outer_radius, center.y - outer_radius, outer_diameter, outer_diameter );

      /* green rim arc */
      g.setColor( NEON_BLUE );
      g.fillArc( center.x - radius, center.y - radius, diameter, diameter, 300, MAX_ANGLE - MIN_ANGLE );
      g.setColor( LIGHT_BLACK );
      g.fillOval( center.x - rim_radius, center.y - rim_radius, rim_diameter, rim_diameter );

      /* speedometer labels */
      Rectangle2D tensMark = new Rectangle2D.Double( center.x, center.y - rim_radius, needle_width + markWidthOffset, 2 * markHeight );
      Rectangle2D fiveMark = new Rectangle2D.Double( center.x, center.y - rim_radius, needle_width + markWidthOffset, markHeight );
      Rectangle2D onesMark = new Rectangle2D.Double( center.x, center.y - rim_radius, needle_width / 2, markHeight );

      arcAngle = MAX_ANGLE - MIN_ANGLE - Math.toDegrees( (double)(needle_width + 2) / rim_radius );
      double increment = arcAngle / MAX_SPEED;
      int speed = 0;
      double theta = MIN_ANGLE;

      /* draw dash marks on Speedometer */
      drawSpeedMark( g, tensMark, theta, NEON_BLUE );
      drawSpeedDigit( g, speed, theta, NEON_BLUE );
      theta += increment;
      speed++;

      for( ; theta < MAX_ANGLE; theta += increment ) {
        /* assign different sized dashes for speed marks */
        Rectangle2D speedMark;
        Color markColor = NEON_BLUE;

        if( speed % 10 == 0 ) speedMark = tensMark;
        else if( speed % 5 == 0 ) speedMark = fiveMark;
        else speedMark = onesMark;
      
        drawSpeedMark( g, speedMark, theta, NEON_BLUE );
        if( speed % 10 == 0 ) drawSpeedDigit( g, speed, theta, NEON_BLUE );
        speed++;
      }

      /* speedometer needle */
      needle = new RoundRectangle2D.Double( center.x, center.y - rim_radius + needle_offset, needle_width, rim_radius - needle_offset, 6, 6 );
      rotateNeedle( g, currentTheta - MIN_ANGLE );

      /* speedometer needle origin */
      g.setColor( METALLIC_BLACK );
      g.fillOval( center.x - inner_radius, center.y - inner_radius, inner_diameter, inner_diameter );
      g.setColor( Color.BLACK );
      g.drawOval( center.x - inner_radius, center.y - inner_radius, inner_diameter, inner_diameter );

      /* draw MPH and Transmission labels */
      g.setFont( speedometerFont );
      g.setColor( Color.WHITE );
      int MPH_width = g.getFontMetrics().stringWidth( MPH_LABEL );
      g.drawString( MPH_LABEL, (int) (center.x - MPH_width / 2.0), center.y + MPH_offset );
      g.setFont( transmissionFont );
      transmission_width = g.getFontMetrics().stringWidth( TRANSMISSION_LABEL );
      /* Assume car is in park */
      setTransmission( g, transmission_state );
    }

    /**
     * Draws speed values around the speedometer.
     * @param g Reference to current canvas.
     * @param speed speed value to draw.
     * @param theta angle that the speed should be drawn on the speedometer.
     * @param color color of speed value to be drawn.
     * @return Nothing.
     */
    private void drawSpeedDigit( Graphics g, int speed, double theta, Color color ) {
      double needle_radius = rim_radius - needle_offset; 
      double x = center.x + needle_radius * Math.cos( Math.toRadians(-((int)theta) + 90) );
      double y = center.y - needle_radius * Math.sin( Math.toRadians(-((int)theta) + 90) );

      g.setFont( transmissionFont );
      g.setColor( color );

      if( speed == MAX_SPEED / 2 ) {
        /* center speed */
	      x -= g.getFontMetrics().stringWidth( Integer.toString(speed) ) / 2;
	      y += g.getFontMetrics().getHeight() / 4;
      }

      if( speed > MAX_SPEED / 2 ) {
        /* shift string reference on right half side of speedometer to */
	      /* bottom-right corner instead of bottom-left */
	      x -= g.getFontMetrics().stringWidth( Integer.toString(speed) );
      }

      if( speed > 10 && speed < 110 ) {
        y += g.getFontMetrics().getHeight() / 2;
      }

      if( speed > 10 && speed < 40 ) {
        y -= g.getFontMetrics().getHeight() / 4;
      }

      if( speed == 50 ) {
        x -= g.getFontMetrics().stringWidth( Integer.toString(speed) ) / 4;
	      y += g.getFontMetrics().getHeight() / 4;
      }

      if( speed == 70 ) {
        x += g.getFontMetrics().stringWidth( Integer.toString(speed) ) / 4;
	      y += g.getFontMetrics().getHeight() / 4;
      }

      g.drawString( Integer.toString(speed), (int)(x), (int)(y) );
    }

    /**
     *  Draws an individual rectangular speedMark on the speedometer.
     *  @param rectangle rectangle that will be drawn.
     *  @param theta angle that the rectangle will be placed. [-150, 150]. 
     *  @param color color of the rectangle.
     *  @return nothing.
     */
    private void drawSpeedMark( Graphics g, Rectangle2D rectangle, double theta, Color color ) {
      //Graphics2D g2d = (Graphics2D) getGraphics();
      Graphics2D g2d = (Graphics2D) g;
      AffineTransform old = g2d.getTransform();
      g2d.setColor( color );
      /* rotate needle around origin */
      g2d.rotate( Math.toRadians(theta), center.x, center.y );
      g2d.fill( rectangle );
      g2d.setTransform( old );
    }

    /**
     *  Rotates needle with a specific needle color.
     *  @param theta Degree needle makes with 0mph mark. Angle range [150, -150].
     *               Angles out of bounds are corrected internally. 
     *  @param color Color of needle.
     *  @return nothing.
     */
    private void rotateNeedle( Graphics g, int theta, Color color ) {
      Graphics2D g2d = (Graphics2D) g;
      AffineTransform old = g2d.getTransform();
      g2d.setColor( color );
      // rotate needle around origin 
      g2d.rotate( Math.toRadians(theta), center.x, center.y );
      g2d.fill( needle );
      g2d.setTransform( old );
    }

    /**
     *  Rotates speedometer needle.
     *  @param theta Degree needle makes with 0mph. Angle range [0, 300]. 
     *               Angles out of bounds are corrected internally. 
     *  @return nothing.
     */
    private void rotateNeedle( Graphics g, int theta ) {
      theta = (theta - MAX_ANGLE) % (MAX_ANGLE + 1);
      rotateNeedle( g, theta, NEON_RED );
    }

    /**
     * Sets transmission state of speedometer.
     * @param g reference to screen canvas.
     * @param state transmission state to be set.
     * @return Nothing.
     */
    private void setTransmission( Graphics g, Transmission state ) {
      String label = TRANSMISSION_LABEL;
    
      char transmission = state.toString().charAt(0);
      g.setFont( transmissionFont );
      int x_startPoint = (int)(center.x - transmission_width / 2.0); 
      int x_offset = g.getFontMetrics().stringWidth( label.substring(0, label.indexOf(transmission)) );
      int y_offset = center.y + 3 * MPH_offset;
      g.setColor( Color.WHITE );
      g.drawString( label, x_startPoint, y_offset );
      g.setColor( DARK_NEON_RED );
      g.drawString( Character.toString(transmission), x_startPoint + x_offset, y_offset );
    }

  } /* class renderPanel */

  /**
   * Maps value in range [min, max] to proportional range [newMin, newMax].
   * @param value Number to mapped.
   * @param min Current minimum of value range.
   * @param max Current maximum of value range.
   * @param newMin New minimum of mapped range.
   * @param newMax New maximum of mapped range.
   * @return new value in from new range.
   */
  public double map( int value, int min, int max, int newMin, int newMax ) {
    if( min > max || newMin > newMax ) return 0;
    if( value < min || value > max ) value = min;
    return newMin + ((double)value) / (max - min) * (newMax - newMin);
  }

  /**
   * Checks if data is a number.
   * @param data string of bytes to be interpretted.
   * @return True if data is a numerical value, false otherwise.
   */
  public boolean isNumeric( String data ) {
    try {
      Integer.parseInt(data);
    }
    catch( NumberFormatException e ) {
      return false;
    }
    return true;
  }

  /**
   * Checks if data string is formatted as speed data.
   * @param data String of data.
   * @return True if data is in the format "Speed: {numbder}".
   */
  private boolean isSpeedData( String data ) {
    /* expected format "Speed: {number}" */
    String[] tokens = data.split("Speed: ");
    if( tokens.length != 2 || !isNumeric(tokens[1]) ) {
      /* incorrect format */ 
      return false;
    }
    int value = Integer.parseInt(tokens[1]);
    if(value < 0 || value > MAX_SPEED) {
      /* out of range */
      return false;
    }
    return true;
  }

  /**
   * Checks if data string is formatted as battery percentage data.
   * @param data String of data.
   * @return True if data is in the format "Battery Percentage: {numbder}".
   */
  private boolean isBatteryPercentageData( String data ) {
    /* expected format "Battery Percentage: {number}" */
    String[] tokens = data.split("Battery Percentage: ");
    if( tokens.length != 2 || !isNumeric(tokens[1]) ) {
      /* incorrect format */
      return false;
    }
    int value = Integer.parseInt(tokens[1]);
    if( value < 0 || value > 100 ) {
      /* out of range */
      return false;
    }
    return true; 
  }

  private boolean isLockedData( String data ) {
    /* Expected format "Locked: {1 or 0}" */
    String[] tokens = data.split("Locked: ");
    if( tokens.length != 2 || !isNumeric(tokens[1]) ) {
      /* incorrect format */
      return false;
    }
    int value = Integer.parseInt(tokens[1]);
    if( value != 0 && value != 1) {
      /* invalid value */
      return false;
    }

    return true;
  }

  /**
   * Checks if data is for transmission.
   * @param data string of bytes to be interpretted.
   * @return True if data is correctly formatted for the transmission, 
   *         false otherwise.
   */
  public boolean isTransmissionData( String data ) {
    /* expected format "Transmission: {state}" */
    String[] tokens = data.split("Transmission: ");
    if( tokens.length != 2 ) {
      /* incorrect format */
      return false;
    }
    if( tokens[1].length() != 1 ) {
      /* must be single character input */
      return false;
    }
    char value = tokens[1].charAt(0);
    for( Transmission state : Transmission.values() ) {
      if(state.toString().charAt(0) == Character.toUpperCase(value) ) {
        /* state exists {P,R,D} */
        return true;
      }
    }
    return false;
  }

  /**
   * Setter for the speed to be displayed on the speedometer.
   * @param speed numerical speed expected to be [0, 120].
   * @return Nothing.
   */
  public void setSpeed( int speed ) {
    if( speed < MIN_SPEED ) speed = MIN_SPEED;
    else if( speed > MAX_SPEED ) speed = MAX_SPEED;
    currentTheta = (int) map( speed, MIN_SPEED, MAX_SPEED, MIN_ANGLE, MAX_ANGLE );
  }
  private void setSpeed( String data ) {
    if( !isSpeedData(data) ) return;
    String[] tokens = data.split("Speed: ");
    setSpeed( Integer.parseInt(tokens[1]) );
  }

  /**
   * Setter for the transmission state of the speedometer.
   * @param state Transmission state {park, reverse, drive}.
   * @return Nothing.
   */
  public void setTransmission( Transmission state ) {
    transmission_state = state;
  }
  private void setTransmission( String data ) {
    if( !isTransmissionData(data) ) return;
    String[] tokens = data.split("Transmission: ");
    for( Transmission state : Transmission.values() ) {
      if( state.toString().charAt(0) == Character.toUpperCase(tokens[1].charAt(0)) ) {
        setTransmission(state);
      }
    }
  }

  /**
   * Setter for the battery percentage of go kart battery pack.
   * @param percentage battery percentage, [0, 100].
   * @return Nothing.
   */
  public void setBatteryPercentage( int percentage ) {
    batteryPercentage = percentage / 100.0;
  }
  private void setBatteryPercentage( String data ) {
    if( !isBatteryPercentageData(data) ) return;
    String[] tokens = data.split("Battery Percentage: ");
    setBatteryPercentage( Integer.parseInt(tokens[1]) );
  }

  /**
   * Setter for the locked state of the go kart.
   * @param locked Go kart is in state locked.
   * @return Nothing.
   */
  public void setLocked( boolean locked ) {
    this.locked = locked;
  }
  private void setLocked( String data ) {
    if( !isLockedData(data) ) return;
    String[] tokens = data.split("Locked: ");
    setLocked( Integer.parseInt(tokens[1]) == 1 );
  }

  /**
   * Getter for the minimum speed on speedometer.
   */
  public int getMinSpeed() {
    return MIN_SPEED;
  }

  /**
   * Getter for the maximum speed on speedometer.
   */
  public int getMaxSpeed() {
    return MAX_SPEED;
  }

  /**
   * Main driver of the dashboard GUI.
   */
  public static void main( String[] args ) {
    DashboardGUI dashboard = new DashboardGUI();
  }

}
