# Proc analysis

Order of events

## Login

* L O G I C
* At the same time:
  * 3 Logicoma logo circles
  * Outer circle being drawn clockwise from left and right point
* "S" center rectangle
* End of "S" being drawn from lower-left and upper-right
* At the same time:
  * Logic OS border being drawn in three shades from lower left and upper right corner (2px thick, ca. 197*60)
  * INITIALISE appears (char-appear times overlap; mid-grey shadow)
* Progress bar appears from left
  * mid grey, dark gray, mid grey white
  * y ~ 137
* Progress bar ends (scrolls to right)
* Loggic OS and "INITIALISE" dissolve
  * Rectangles with colors dark, mid, white, mid, dark
  * Rectangle order
    * 5 . 
    * 4 9
    * 3 8
    * 2 7
    * 1 6
  * 5 rects high, 14 rects w
  * rect size: 15*15
* Input field appears
  * similar to logo outline rectangle; all shades at the tip, starting lower-left and upper-right
  * 102*11
* "USERNAME" appears (plain white, no shadow)
* Input field cursor appears (mid-grey, )
  * 7*7
* Input field cursor fades out
* "HOFFMAN" appears
  * Not synced with cursor fade-in/out, cursor keeps its fade timing
* White cursor-sized square appears left, moves to right, ereases input field contents
* "USERNAME" disappears (starting from left)
* "PASSWORD" appears
* Circles appear
  * 7 chars entered
  * 7 * backspace
  * 11 chars entered
* White cursor-sized square appears left, moves to right, ereases input field contents
* Input field outline disappears
* "PASSWORD" disappears

## App start

* Logicoma mini logo appears lower-left
* Clock border appears lower-right
* "Taskbar" separator line appears left-to-right
* Text "LOADING"/"USER"/"PROFILE" appears (V/H centered)
* "13:37" appears
* Icons appear
  * Magnifier
  * Letter
  * Chip
  * Keyboard keys
  * Satellite
  * Skull key
  * Download arrow
  * Distance between icons: 37; y ~ 182, first x ~ 24
* Flipping through icons
  * Icon bg highlight (ligher grey)
  * Icon name appears vertically
  * SEARCH, MESSAGING, DEMOS, TRACKER
* Icon selected (blue bg)

## Tracker

* "LOADING" / "TRACKER"
  * Flashes white 2 times
* Circling progress thingie
* Title: "TRACKER" upper left in icon-name font; outlined
* Stop, play, record buttons appear
* "TITLE: REVISION 22 03" / "BPM: 125"
* Grid: KICK, HAT, SNARE, COWBELL, BASS
etc.

## Proc renaming

Handy so far. Picked up on the way:

* wire = global variables! oh
* BC_WSTATE with higher-than-state indexes = wire state
* BC_TAIL is a goto to the proc in `state.proc`

## More renaming

Over time, the decompiled script started to become more and more readable.

    proc_006 = drawpixel
    proc_014 = drawline_len_speed
    proc_109 = draw_message_subjects
    proc_197 = draw_multiple_threats_detected
    proc_196 = draw_comms_observed
    proc_110 = draw_hey_man_uhh_message
    proc_183 = draw_format_decrypt_key
    proc_061 = draw_search_messaging_demos_tracker_scanner_cracker_receiver
    proc_076 = draw_title_revision_22_03_bpm_125
    proc_150 = draw_idle_analysing_sample_cycling_ai_keys_decrypt
    proc_048 = draw_loading
    ...

etc.
