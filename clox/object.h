#ifndef clox_object_h
#define clox_object_h

#include "common.h"
#include "value.h"

#define OBJ_TYPE(value)     (value.as.obj->type)
#define IS_STRING(value)    isObjType(value, OBJ_STRING)

#define AS_STRING(value)    ((ObjString*)value.as.obj)
#define AS_CSTRING(value)   (((ObjString*)value.as.obj)->chars)

typedef enum {
    OBJ_STRING,
} ObjType;

struct Obj {
    ObjType type;
    struct Obj* next;
};

struct ObjString {
    Obj obj;
    int length;
    char* chars;
    uint32_t hash;
};

ObjString* takeString(char* chars, int length);
ObjString* copyString(const char* chars, int length);
void printObject(Value value);

static inline bool isObjType(Value value, ObjType type) {
    return IS_OBJ(value) && value.as.obj->type == type;
}

#endif