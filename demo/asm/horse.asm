	include	"lvo/exec_lib.i"
	include	"lvo/dos_lib.i"

MODE_OLDFILE	=	1005
USE_VISUALIZER_OUTPUT	= 0

ENABLE_MUSIC	=	1

COMPATIBILITY	=	1
FASTMEM		=	0
RMBPAUSE	=	0
FPUINT		=	0
COPPER		=	1
BLITTER		=	1
SPRITE		=	0
TOPAZ		=	0
SECTIONHACK	=	-1
SCREENHEIGHT	=	180
; ---------------------------------------------------------------------------

	section	code,code

; ---------------------------------------------------------------------------
CODE_START:

	include macros.asm
	include	DemoStartup.S

	include	Rose.S
	include	Sinus.S


_Precalc:
	lea.l	RoseSinus,a0
	bsr.w	MakeSinus

	lea.l	ColorScript,a1
	lea.l	Constants,a2
	lea.l	Bytecode,a3
	lea.l	RoseSinus,a4
	lea.l	RoseChip,a5
	lea.l	RoseSpace,a6
	bsr.w	RoseInit
	rts

_Main:
	; _mt_init(a6=CUSTOM, a0=TrackerModule, a1=Samples|NULL, d0=InitialSongPos.b)
	lea	$dff000,a6
	lea	song,a0
	sub.l	a1,a1
	moveq	#0,d0
	bsr	_mt_init
	; _mt_install_cia(a6=CUSTOM, a0=VectorBase, d0=PALflag.b)
	lea	$dff000,a6
	sub.l	a0,a0
	moveq	#1,d0
	bsr	_mt_install_cia
	; Main demo routine, called by the startup.
	; Demo will quit when this routine returns.
	lea.l	RoseSpace,a6
	bsr.w	RoseMain
	rts

_Interrupt:
	; Called by the vblank interrupt.
	lea.l	RoseSpace,a6

 tst.l r_VBlank(a6)
 beq.b .nomus
	st.b	_mt_Enable
.nomus
	tst.l	r_Ready(a6)
	beq.b	.notready
	; Skip all vblank tasks (including music) until Rose is ready.
	bsr.w	RoseInterrupt
.notready:	rts

_Exit:
	rts

; ptplayer
; MINIMAL=1
	include ptplayer.asm

CODE_END:
; ---------------------------------------------------------------------------

	section	data,data

; ---------------------------------------------------------------------------
DATA_START:

	if USE_VISUALIZER_OUTPUT
ColorScript:	incbin ../Rose/visualizer/colorscript.bin
		even
Bytecode:	incbin ../Rose/visualizer/bytecodes.bin
		even
Constants:	incbin ../Rose/visualizer/constants.bin
		even
	else
ColorScript:	incbin colorscript.bin
		even
Bytecode:	incbin bytecodes.bin
		even
Constants:	incbin constants.bin
		even
	endif

DATA_END:
; ---------------------------------------------------------------------------

	section	bss,bss

; ---------------------------------------------------------------------------
BSS_START:

RoseSpace:	ds.b	ROSE_FASTSIZE
RoseSinus:	ds.w	DEGREES

BSS_END:
; ---------------------------------------------------------------------------

	section	bss_c,bss_c

; ---------------------------------------------------------------------------
BSS_C_START:

RoseChip:	ds.b	ROSE_CHIPSIZE

BSS_C_END:
; ---------------------------------------------------------------------------

	section data_c,data_c

; ---------------------------------------------------------------------------
CHIP_START:

song:	incbin	mod.logicos

CHIP_END:

; ---------------------------------------------------------------------------
CODE_SIZE	= CODE_END-CODE_START
DATA_SIZE	= DATA_END-DATA_START
CHIP_SIZE	= CHIP_END-CHIP_START
BSS_SIZE	= BSS_END-BSS_START
BSS_C_SIZE	= BSS_C_END-BSS_C_START

		printt 'CODE'
		printv CODE_SIZE
		printt 'DATA'
		printv DATA_SIZE
		printt 'CHIP'
		printv CHIP_SIZE
		printt 'BSS'
		printv BSS_SIZE
		printt 'BSS_C'
		printv BSS_C_SIZE
