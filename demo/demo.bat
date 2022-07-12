@if not exist util\winuae.exe (
  echo Please put a current winuae.exe into directory "util".
  goto :fail
)

@if not exist util\kick13.rom (
  echo Please put a kick13.rom file into directory "util".
  goto :fail
)

@start util\winuae.exe -config=%~dp0util\Configurations\A500.uae -s floppy0=%~dp0logicos.adf -k %~dp0util\kick13.rom
@exit /b

:fail

@echo Cannot run. :(



