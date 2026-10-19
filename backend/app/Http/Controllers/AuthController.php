<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;
use Illuminate\Validation\Rules\Password;

//accounts: register, login, read the signed-in account and logout
class AuthController extends Controller
{
    //POST /api/register - create an account and return its login token
    public function register(Request $request): JsonResponse
    {
        //2.input: tidy the name and email first, then check every field
        $request->merge([
            'name' => $this->trimText($request->input('name')),
            'email' => $this->cleanEmail($request->input('email')),
        ]);
        $data = $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'email' => ['bail', 'required', 'string', 'max:254', 'email:rfc', 'unique:users,email'],
            'password' => ['required', 'string', Password::min(8)->letters()->numbers()],
        ]);

        //3.process: the User model hashes the password before it is saved
        $user = User::create($data);
        $token = $user->createToken('android')->plainTextToken;

        //4.output
        return response()->json([
            'token' => $token,
            'user' => $user,
        ], 201);
    }

    //POST /api/login - check the email and password and return a new login token
    public function login(Request $request): JsonResponse
    {
        //2.input
        $request->merge([
            'email' => $this->cleanEmail($request->input('email')),
        ]);
        $data = $request->validate([
            'email' => ['bail', 'required', 'string', 'max:254', 'email:rfc'],
            'password' => ['required', 'string', 'max:255'],
        ]);

        //3.process: the same message is used for a wrong email and a wrong password,
        //so nobody can find out which emails have an account
        $user = User::where('email', $data['email'])->first();
        if ($user === null || ! Hash::check($data['password'], $user->password))
        {
            throw ValidationException::withMessages([
                'email' => ['The provided credentials are incorrect.'],
            ]);
        }

        //keep one active app session per owner: old tokens stop working
        $user->tokens()->delete();
        $token = $user->createToken('android')->plainTextToken;

        //4.output
        return response()->json([
            'token' => $token,
            'user' => $user,
        ]);
    }

    //GET /api/me - the account that owns the token sent by the app
    public function me(Request $request): JsonResponse
    {
        //4.output
        return response()->json($request->user());
    }

    //POST /api/logout - delete the token the app used, so it cannot be used again
    public function logout(Request $request): JsonResponse
    {
        //3.process
        $token = $request->user()->currentAccessToken();
        if ($token !== null)
        {
            $token->delete();
        }

        //4.output
        return response()->json(['message' => 'Signed out successfully.']);
    }

    //2.input

    //remove spaces around text; anything that is not text is left for validation to reject
    private function trimText(mixed $value): mixed
    {
        if (is_string($value))
        {
            return trim($value);
        }
        return $value;
    }

    //" Demo@PawCare.test " becomes "demo@pawcare.test"
    private function cleanEmail(mixed $value): mixed
    {
        if (is_string($value))
        {
            return strtolower(trim($value));
        }
        return $value;
    }
}
