#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>

#define MAX_USERNAME_LEN 100 //Set your user buff lenghts
#define MAX_MESSAGE_LEN 200

// Syscall numbers
#define __NR_cs1550_send_msg 441 //Here is syscall #s
#define __NR_cs1550_get_msg 442

//Send_message wrapper
int send_message(char *to, char *msg, char *from){
    return syscall(441, to, msg, from); //either returns 0 or -1 for error
}

//recieve_message wrapper but i implemented return #s logic(implementation detail)
int receive_messages(char *to, char *msg, long msg_len, char *from, long from_len ) {

    int result = syscall(442, to, msg, msg_len, from, from_len);// Returns either less than 0 for no messages, 1 for multiple  messages, and 0 for 1 message
    if (result < 0) {
        printf("No messages found.\n");
        return result;
    }

    while (result == 1) { //While more messages keep printing until the final message is found and exit loop
        printf("%s said: \"%s\"\n", from, msg);
        result = syscall(442, to, msg, msg_len, from, from_len);
    }
    // If syscall returns 0 we just print the one message and return or
    // in the while loop result was set to 0 so print the final message
    printf("%s said: \"%s\"\n", from, msg);

    return result;
}

int main(int argc, char *argv[]) {
    if (argc < 2) { //Error handling explaining how to run program
        printf("Usage: %s [-s username message | -r]\n", argv[0]);
        return 1;
    }
    char *retMsg = malloc(MAX_MESSAGE_LEN);  //Allocate user buffer for kernels return 
    if (!retMsg) { //Error handle malloc
         printf("Memory allocation failed.\n");
        free(retMsg);
        return 1;
    }
    char *retFrom = malloc(MAX_USERNAME_LEN);  //Allocate user buffer for kernels return 
    if (!retFrom) {//Error handle malloc
        printf("Memory allocation failed.\n");
        free(retFrom);
        return 1;
    }

    if ((strcmp(argv[1], "-s") == 0) && (argc == 4)) { //If usage -s send message
        // Send message
        int ret = send_message(argv[2], argv[3], getenv("USER")); //With args to, the message, and whos sending it
        if (ret != 0) { //Error handle
            printf("Failed to send message.\n");
            return 1;
        }
    } else if (strcmp(argv[1], "-r") == 0 && argc == 2) { //If usage -r retrieve your messages
        int ret = receive_messages(getenv("USER"), retMsg, MAX_MESSAGE_LEN, retFrom, MAX_USERNAME_LEN);//With args Curr user, retMsg buffer, Max message size, who send it, and max username len
        if (ret != 0) {//Error handle
            printf("Failed to receive messages.\n");
            return 1;
        }
    } else { //if argc > 3
        printf("Invalid command. Usage: %s [-s username message | -r]\n", argv[0]);
        return 1;
    }
    free(retMsg); //Free allocated memory
    free(retFrom);
    return 0;
}
