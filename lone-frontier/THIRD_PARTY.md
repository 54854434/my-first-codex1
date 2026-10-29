# Open-source components

## canvas-vampire-survivors

Source: https://github.com/ricardo-foundry/canvas-vampire-survivors

License: MIT, Copyright (c) 2024 Survivor Game.

- `app/src/main/assets/spatial-hash.js` adapts `src/spatial-hash.js`, upstream blob `bb9f6ee431f423c6e83d5c9fff0905fbb5c2d813`. The implementation is retained; ES module packaging is changed to a local classic-script / CommonJS wrapper for Android's offline file loader.
- `app/src/main/assets/input.js` adapts the virtual joystick's deadzone and nonlinear response from `src/input.js`. Input registration is reimplemented with tracked pointer IDs and capture for simultaneous joystick and skill touches.
- Full notice distributed in `app/src/main/assets/licenses/survivor-MIT.txt`, and available through the in-game manual's open-source credits.

## uRTS

Source: https://github.com/trydionel/uRTS

License: MIT, Copyright (c) 2014 Jeff Tucker.

- `app/src/main/assets/steering.js` adapts `util/math.js`, upstream blob `aab85d57080353f9b04e9b8de389964f723c7f33`, exposing interpolation and clamping without modifying the global Math object.
- Full notice distributed in `app/src/main/assets/licenses/uRTS-MIT.txt`.

The military campaign, army orders, capture and recapture logic, rebirth and research, interface, procedural military artwork, and sound synthesis are implemented for this game. No commercial game art or proprietary assets are included.
