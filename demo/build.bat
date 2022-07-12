@echo off
@set ASMFILE=asm\horse.asm
@set OBJFILE=build\horse.o
@set EXEFILE=build\horse.exe

@echo Assemble %ASMFILE% ...
@del %EXEFILE% >nul 2>&1
@rem util\vasmm68k_mot -x -nosym -maxerrors=50 -m68020 -no-opt -Fhunk -align -phxass -o "%OBJFILE%" -I"..\Rose\engine" -I"build" -I"asm" -quiet "%ASMFILE%"
util\vasmm68k_mot -x -nosym -maxerrors=50 -m68020 -no-opt -Fhunk -align -o "%OBJFILE%" -I"..\Rose\engine" -I"build" -I"asm" -I"..\sfx" -quiet "%ASMFILE%"
@if %ERRORLEVEL% NEQ 0 goto failed
@echo Link     %EXEFILE% ...
util\vlink -bamigahunk -o %EXEFILE% -s %OBJFILE%
exit /b

:failed
echo Build failed!
