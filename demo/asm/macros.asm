	macro DEBUGBREAK
	move.w	#$4000,$dff09a
.\@1	move.w	#\1,$dff180
	bra.b	.\@1
	endm
