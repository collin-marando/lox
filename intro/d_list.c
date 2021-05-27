#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CAPACITY 100

typedef enum { false, true } bool;

typedef struct Node {
    char *value;
    struct Node *prev, *next;
} Node;

typedef struct List {
    Node *head, *tail;
    int size;
} List;

bool insert(List* list, char* value, int index) {

    // If index OOB, return false
    if (index > list->size || list->size == CAPACITY){ return false; }

    // Iterate to list Node @ index
    Node *curr = list->head;
    Node *prev = NULL;
    for(int i = 0; i < index; i++){
        prev = curr;
        curr = curr->next;
    }

    // Initialize new Node
    Node *temp = (Node*) malloc(sizeof(Node));
    temp->value = (char*) malloc(strlen(value)+1);
    temp->prev = NULL;
    temp->next = NULL;

    // Set temp Node value
    strcpy(temp->value, value); 

    // Update neighbouring Node pointers
    if (prev) {
        temp->prev = prev;
        prev->next = temp;
    }
        
    if (curr){
        temp->next = curr;
        curr->prev = temp;
    }

    // Update list pointers
    if (index == 0)
        list->head = temp;

    if (index == list->size)
        list->tail = temp;

    list->size++;

    return true;
}

int find(List* list, char* value) {

    // Iterate through list until match or end
    Node *curr = list->head;
    for(int i = 0; i < list->size; i++){
        if(strcmp(curr->value, value) == 0)
            return i;
        curr = curr->next;
    }

    return -1;
}

bool delete(List* list, int index){

    if (index >= list->size || list->size == 0) { return false; }
    
    // Iterate to list Node @ index
    Node *curr = list->head;
    for(int i = 0; i < index; i++)
        curr = curr->next;

    // Update neighbouring Node pointers
    if(curr->next)
        curr->next->prev = curr->prev;
    
    if(curr->prev)
        curr->prev->next = curr->next;

    // Update list pointers
    if(index == 0)
        list->head = list->head->next;

    if(index == list->size - 1)
        list->tail = list->tail->prev;

    // Deallocate memory for selected Node
    free(curr);

    list->size--;

    return true;
}

void print(List* list){
    printf("List: [");
    Node *curr = list->head;
    for(int i = 0; i < list->size; i++){
        printf("\"%s\", ", curr->value);
        curr = curr->next;
    }
    printf("]\n");
}

int main() {

    // Initialize list
    List *list = (List*) malloc(sizeof(List));
    *list = (List){NULL, NULL, 0};
    
    insert(list, "test", 0);    //Insert into empty list
    insert(list, "test1", 0);   //Insert to front
    insert(list, "test2", 2);   //Insert to back
    insert(list, "test3", 1);   //Insert in middle
    insert(list, "test4", 9);   //Invalid insert

    print(list);

    // Verify list and Node pointers
    printf("Comp: \"%s\", \"%s\", \"%s\", \"%s\"\n", 
        list->head->value, 
        list->head->next->value,
        list->tail->prev->value,
        list->tail->value 
    );

    printf("Find: \"test\" @ index: %d\n", find(list, "test"));
    printf("Find: \"test4\" @ index: %d\n", find(list, "test4"));

    delete(list, 1);    //delete in middle
    print(list);
    delete(list, 2);    //delete at back
    print(list);
    delete(list, 0);    //delete at front
    print(list);
    delete(list, 0);    //delete to empty
    print(list);

    return 0;
}