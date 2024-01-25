// RGB_TO_COLOR(31, 0, 0));   // Red
// RGB_TO_COLOR(0, 63, 0));   // Green
// RGB_TO_COLOR(0, 0, 31));   // Blue
// RGB_TO_COLOR(31, 63, 31)); // White
#include "graphics.h"
int main(int argc, char **argv)
{
	clear_screen();
	init_graphics();

	char key;

	do
	{
		draw_text(210, 40, "Press 'q' to exit.", RGB_TO_COLOR(31, 63, 31));
		draw_text(210, 60, "Press 's' to draw action figures body.", RGB_TO_COLOR(31, 0, 0));
		draw_text(210, 80, "Press 'o' to draw action figures face.", RGB_TO_COLOR(0, 63, 0));
		// 	//draw_circle(x, y, 20, 15);

		key = getkey();

		// draw a red rectangle
		if (key == 's')
		{
			draw_rect(210, 100, 100, 100, RGB_TO_COLOR(31, 0, 0));
			draw_rect((210/2)+10, 200, 300, 70, RGB_TO_COLOR(31, 0, 0));
			draw_rect(210, 270, 100, 100, RGB_TO_COLOR(31, 0, 0));
			draw_rect(220, 370, 20, 100, RGB_TO_COLOR(31, 0, 0));
			draw_rect(270, 370, 20, 100, RGB_TO_COLOR(31, 0, 0));
		}
		// draw a face
		if (key == 'o')
		{ 
			draw_circle(258, 140, 30, RGB_TO_COLOR(0, 63, 0));
			draw_circle(245, 130, 10, RGB_TO_COLOR(0, 0, 31));
			draw_circle(270, 130, 10, RGB_TO_COLOR(0, 0, 31));
		}
		sleep_ms(5000);
	} while (key != 'q');

	exit_graphics();

	return 0;
}
